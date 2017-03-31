(ns rankly.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [ajax.core :refer [GET POST]])
    (:require-macros [rankly.macros :as rankly]))

;; -------------------------
;; Views

(enable-console-print!)

(defn home-page []
  [:div [:h2 "Welcome to rankly"]
   [:div [:a {:href "/about"} "go to about page xd"]]])

(defn about-page []
  [:div [:h2 (str "About rankly: ")]
   [:div [:a {:href "/"} "go to the home page xd"]]])

(defn ranking-page []
  (let [{:keys [id]} (session/get :params)
        local-state (reagent/atom {:loaded false})]
    (GET
        (str "/api/ranking/" id)
        {:handler (fn [res]
                    (reset! local-state {:loaded true
                                         :ranking res}))})
    (fn []
      (if (:loaded @local-state)
        [:div
         [:h2 (get-in @local-state [:ranking "ranking/title"])]
         [:ol
          (for [element (get-in @local-state [:ranking "ranking/rank"])]
            ^{:key (get element "db/id")} [:li (get element "element/name")])]]
        [:h2 "loading..."]))))

(defn matchup-page []
  (let [{:keys [id]} (session/get :params)
        local-state (reagent/atom {:loaded false})
        get-next (fn []
                   (GET
                       (str "/api/ranking/" id "/matchup")
                       {:handler (fn [res]
                                   (reset! local-state {:loaded true
                                                        :match res}))}))
        reporter (fn [wid lid]
                   (fn [_]
                     (reset! local-state {:loaded false})
                     (GET
                         (str "/api/ranking/" id "/matchup/" wid "/" lid)
                         {:handler (fn [_] (get-next))})))
        get-field-on (fn [field on] (-> @local-state :match on (get field)))]
    (get-next)
    (fn []
      (if (:loaded @local-state)
        [:div
         [:button {:on-click (reporter (get-field-on "db/id" first)
                                       (get-field-on "db/id" last))}
          (get-field-on "element/name" first)]
         [:button {:on-click (reporter (get-field-on "db/id" last)
                                       (get-field-on "db/id" first))}
          (get-field-on "element/name" last)]]
        [:h2 "loading..."]))))

(defn current-page [] [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(rankly/defroute "/" #'home-page)
(rankly/defroute "/about" #'about-page)
(rankly/defroute "/ranking/:id" #'ranking-page)
(rankly/defroute "/ranking/:id/matchup" #'matchup-page)

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render-component [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
