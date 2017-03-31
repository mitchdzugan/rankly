(ns rankly.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [rankly.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [datomic.api :as d]
            [relative.trueskill :as ts]
            [bigml.sampling.simple :as simple]
            [clojure.algo.generic.functor :as f]
            [relative.rating :as r]))

(def conn (d/connect "datomic:dev://localhost:4334/rankly"))

(def tse (ts/trueskill-engine))

(defn get-trueskills [qres]
  (->> (:ranking/results qres)
       (reduce (fn [true-skills {{winning-id :db/id} :result/winning-element
                                 {losing-id :db/id} :result/losing-element}]
                 (let [[wts lts] (r/match tse
                                          (get true-skills winning-id)
                                          (get true-skills losing-id))]
                   (merge true-skills {winning-id wts
                                       losing-id lts})))
               (into {} (map #(-> [(:db/id %) (r/player tse {:id (:db/id %)})])
                             (:ranking/elements qres))))))

(defn get-matchup [ranking]
  (let [db (d/db conn)
        qres (d/pull db
                     '[{:ranking/elements [:db/id :element/name :element/image-url]
                        (limit :ranking/results nil) [:result/winning-element :result/losing-element]}]
                     ranking)
        true-skills (get-trueskills qres)
        {:keys [ranking/elements ranking/results]} qres
        first-weights (->> results
                           (mapcat #(map (comp :db/id second) %))
                           frequencies
                           (f/fmap #(/ 1 %))
                           (merge (f/fmap (fn [a] 100) true-skills)))
        first-element (first (simple/sample (map :db/id elements)
                                            :weigh first-weights))
        second-weights (f/fmap #(/ 1
                                   (+ 0.01
                                      (Math/pow (- (:mean %)
                                                   (get-in true-skills [first-element :mean]))
                                                2)))
                               true-skills)
        first-opponents (->> results
                             (map #(map (comp :db/id second) %))
                             (map #(into #{} %))
                             (filter #(get % first-element))
                             (map #(-> % (clojure.set/difference #{first-element}) first)))
        first-never-matched (clojure.set/difference
                             (->> elements (map :db/id) (remove #(= % first-element)) (into #{}))
                             (->> first-opponents (into #{})))
        second-poss (if (= 0 (count first-never-matched))
                      (->> first-opponents
                           frequencies
                           (group-by last)
                           (sort-by first)
                           first
                           last
                           (map first))
                      first-never-matched)
        second-element (first (simple/sample second-poss
                                             :weigh second-weights))]
    (filter #(get #{first-element second-element} (:db/id %)) elements)))

(defn report-matchup [ranking winner loser]
  @(d/transact conn [{:db/id ranking
                      :ranking/results "newres"}
                     {:db/id "newres"
                      :result/losing-element loser
                      :result/winning-element winner}]))

(defn get-ranking [ranking]
  (let [db (d/db conn)
        qres (d/pull db
                     '[{:ranking/elements [:db/id :element/name :element/image-url]
                        (limit :ranking/results nil) [:result/winning-element :result/losing-element]}]
                     ranking)
        true-skills (get-trueskills qres)]
    (->> qres
         :ranking/elements
         (map #(merge % {:rating (get-in true-skills [(:db/id %) :mean])
                         :std-dev (get-in true-skills [(:db/id %) :std-dev])}))
         (sort-by :rating))))

(defn clear-results [ranking]
  (let [db (d/db conn)]
    @(d/transact conn (->> ranking
                           (d/pull db '[(limit :ranking/results nil)])
                           :ranking/results
                           (map #(-> [:db.fn/retractEntity (:db/id %)]))))))

(defn do-one-random [ranking]
  (let [[s1 s2] (get-matchup ranking)]
    (report-matchup ranking (:db/id s1) (:db/id s2)))
  nil)

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page []
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-js "/js/app.js")]))


(defroutes routes
  (GET "/" [] (loading-page))
  (resources "/")
  (not-found (loading-page)))

(def app (wrap-middleware #'routes))

