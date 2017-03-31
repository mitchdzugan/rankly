(ns rankly.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.util.json-response :as jr]
            [hiccup.page :refer [include-js include-css html5]]
            [rankly.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [datomic.api :as d]
            [relative.elo :as elo]
            [bigml.sampling.simple :as simple]
            [clojure.algo.generic.functor :as f]
            [relative.rating :as r]))

(def conn (d/connect "datomic:dev://localhost:4334/rankly"))

(defn get-ratings [qres]
  (let [eng (elo/elo-engine)]
    (->> (:ranking/results qres)
         (reduce (fn [ratings {{winning-id :db/id} :result/winning-element
                                   {losing-id :db/id} :result/losing-element}]
                   (let [[wts lts] (r/match eng
                                            (get ratings winning-id)
                                            (get ratings losing-id))]
                     (merge ratings {winning-id wts
                                         losing-id lts})))
                 (into {} (map #(-> [(:db/id %) (r/player eng {:id (:db/id %)})])
                               (:ranking/elements qres)))))))

(defn get-matchup [ranking]
  (let [db (d/db conn)
        qres (d/pull db
                     '[{:ranking/elements [:db/id :element/name :element/image-url]
                        (limit :ranking/results nil) [:result/winning-element :result/losing-element]}]
                     ranking)
        ratings (get-ratings qres)
        {:keys [ranking/elements ranking/results]} qres
        first-weights (->> results
                           (mapcat #(map (comp :db/id second) %))
                           frequencies
                           (f/fmap #(/ 1 %))
                           (merge (f/fmap (constantly 100) ratings)))
        first-element (first (simple/sample (map :db/id elements)
                                            :weigh first-weights))
        second-weights (f/fmap #(/ 1
                                   (+ 0.01
                                      (Math/pow (- (:rating %)
                                                   (get-in ratings [first-element :rating]))
                                                2)))
                               ratings)
        second-poss (->> results
                         (map #(map (comp :db/id second) %))
                         (map #(into #{} %))
                         (filter #(get % first-element))
                         (map #(-> % (clojure.set/difference #{first-element}) first))
                         frequencies
                         (merge (->> ratings
                                     (remove (fn [[id _]] (= id first-element)))
                                     (into {})
                                     (f/fmap (constantly 0))))
                         (group-by last)
                         (sort-by first)
                         first
                         last
                         (map first))
        second-element (first (simple/sample second-poss
                                             :weigh second-weights))]
    (filter #(get #{first-element second-element} (:db/id %)) elements)))

(defn report-matchup [ranking winner loser]
  @(d/transact conn [{:db/id ranking
                      :ranking/results "newres"}
                     {:db/id "newres"
                      :result/losing-element loser
                      :result/winning-element winner}])
  {})

(defn get-ranking [ranking]
  (let [db (d/db conn)
        qres (d/pull db
                     '[:ranking/title
                       {:ranking/elements [:db/id :element/name :element/image-url]
                        (limit :ranking/results nil) [:result/winning-element :result/losing-element]}]
                     ranking)
        ratings (get-ratings qres)]
    (-> qres
        (dissoc :ranking/elements :ranking/results)
        (merge {:ranking/rank (->> qres
                                   :ranking/elements
                                   (map #(merge % {:rating (get-in ratings [(:db/id %) :rating])}))
                                   (sort-by :rating)
                                   reverse)}))))

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
  (GET "/api/ranking/:id" [id] (jr/json-response (get-ranking (. Long parseLong id))))
  (GET "/api/ranking/:id/matchup" [id] (jr/json-response (get-matchup (. Long parseLong id))))
  (GET "/api/ranking/:id/matchup/:wid/:lid" [id wid lid]
    (jr/json-response (report-matchup (. Long parseLong id)
                                      (. Long parseLong wid)
                                      (. Long parseLong lid))))
  (resources "/")
  (not-found (loading-page)))

(def app (wrap-middleware #'routes))

