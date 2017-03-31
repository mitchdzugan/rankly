(ns rankly.middleware
  (:require [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn wrap-middleware [handler]
  (-> handler
      wrap-json-response
      (wrap-defaults site-defaults)
      wrap-exceptions
      wrap-reload
      ))
