(ns flowdock-xmpp.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.nodejs :as node]
            [cljs.core.async :refer [<!]]
            [flowdock-xmpp.flowdock
             :refer [get-resource sessions update-streams!]
             :as flowdock]
            [flowdock-xmpp.stanza :as stanza]
            [flowdock-xmpp.common :refer [domain jid update-streams!]]
            [flowdock-xmpp.utils :refer [element]]))

;; Use node's stdout
(enable-console-print!)

;; Enable sourcemaps for better node.js stacktraces
(when (not= (-> js/process (.-env) (.-NODE_ENV)) "production")
  (.install (js/require "source-map-support")))

(defn authenticate
  "Checks if the token used in user's jid is valid."
  [opts cb]
  (go (let [token (.toString (.-jid opts))
            response (<! (get-resource token "user"))]
        (if response
          (cb nil opts)
          (cb (js/Error. "Authentication failure") nil)))))

(defn online
  "Sets up listeners for new flowdock events and stores client info."
  [client]
  (when-let [jid (.-jid client)]
    (go
      (let [token (.-user jid)
            user (<! (get-resource token "user"))
            session (flowdock/new-session token)]
        (swap! sessions assoc token {:client client
                                     :session session
                                     :user user
                                     :flows []
                                     :token token
                                     :jid (str jid)})
        (update-streams! token nil)))))

(defn disconnect
  "Cleans up the listeners for client"
  [client]
  (let [jid (.-jid client)
        token (and jid (.-user jid))
        session (and token (@sessions token))]
    (when session
      (.end (:stream session))
      (swap! sessions dissoc token))))

(defn -main
  "Creates a Client2Server XMPP Server and starts listening for
  connections. This is the entry point of flowdock-xmpp."
  [& args]

  (let [C2SServer (.-C2SServer (node/require "node-xmpp-server"))
        key-path (-> js/process (.-env) (.-FLOWDOCK_XMPP_KEY))
        cert-path (-> js/process (.-env) (.-FLOWDOCK_XMPP_CERT))
        opts {:port 5222, :domain domain}
        opts (if (and key-path cert-path)
               (assoc opts :tls {:keyPath key-path, :certPath cert-path})
               opts)
        server (C2SServer. (clj->js opts))]
    (.on server "connect"
         (fn [client]
           (doto client
             (.on "authenticate" authenticate)
             (.on "online" (partial online client))
             (.on "disconnect" (partial disconnect client))
             (.on "stanza"
                  (fn [stanza]
                    (go (when-let [response (<! (stanza/handle client stanza))]
                          (.send client (element response)))))))))))

;; Fire -main when the app is started using command-line.
(set! *main-cli-fn* -main)
