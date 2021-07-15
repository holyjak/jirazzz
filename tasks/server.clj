(ns server
  (:require
    [cheshire.core :as json]
    [org.httpkit.server :as server]))


(def responses (atom []))
(def requests (atom []))


(defn add-responder
  [response]
  (swap! responses conj response))


(defn reset
  []
  (reset! requests [])
  (reset! responses []))


(defn respond
  [{:keys [log?]} req]
  (swap! requests conj (select-keys req [:uri
                                         :method
                                         :body
                                         :path
                                         :query-string]))
  (loop [rs @responses]
    (if-let [r (first rs)]
      (let [match (-> r
                      :match
                      (or {}))]
        (when log?
          (prn "Match?" match (select-keys req (keys match))))
        (if (= match (select-keys req (keys match)))
          (select-keys r [:body :headers :status])
          (recur (rest rs))))
      {:status 404})))


(defn start
  [& {:keys [port log?] :or {port 54646} :as opts}]
  (when log?
    (println "Starting server on" port))
  (server/run-server
    (fn [{:keys [body uri] :as req}]
      (when log?
        (prn "Received:" req))
      (cond
        (= uri "/respond")
        (do
          (add-responder (json/parse-string (slurp body) true))
          {:status 204})

        (= uri "/reset")
        (do
          (reset)
          {:status 204})

        (= uri "/requests")
        {:status 200
         :headers {"content-type" "application/json"}
         :body (-> {:requests @requests}
                   json/generate-string)}

        :else
        (respond opts req)))
    {:port port}))

(defn wait
  []
  (start {:log? true})
  @(promise))