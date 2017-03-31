(ns rankly.macros)

(defmacro defroute
  [path sym]
  `(secretary.core/defroute ~path {:as ~'params}
    (reagent.session/put! :current-page ~sym)
    (reagent.session/put! :params ~'params)))
