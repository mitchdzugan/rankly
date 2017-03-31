(ns rankly.middleware
  (:require [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn wrap-middleware [handler]
  (wrap-json-response (wrap-defaults handler site-defaults)))
