(ns flowdock-xmpp.common
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.nodejs :as node]
            [cljs.core.async :refer [<!]]
            [flowdock-xmpp.flowdock :refer [sessions get-flow get-resource]]
            [flowdock-xmpp.utils :refer [element]]))

(def domain (-> js/process (.-env) (.-FLOWDOCK_XMPP_DOMAIN)
                (or (.hostname (node/require "os")))))

(def not-implemented [:error {:type "cancel"
                              :code "501"}
                      [:feature-not-implemented
                       {:xmlns "urn:ietf:params:xml:ns:xmpp-stanzas"}]])

(defn jid [user] (str (:id user) "@" domain))

(defn handle-message
  "Forwards an incoming flowdock message to the client associated with
  a flowdock session."
  [session message]
  (when (string? (:content message))
    (go
      (let [client (:client session)
            token (:token session)
            flow-id (:flow message)
            flow (and flow-id (<! (get-flow (:session session) flow-id)))
            user-id (:user message)]
        (when (not= user-id (-> session :user :id str))
          (if flow
            ;; This is a private message
            (let [org-id (-> flow :organization :parameterized_name)
                  room-id (str org-id "#" (:parameterized_name flow))
                  user (first (filter #(= (str (:id %)) user-id) (:users flow)))]
              (.send client
                     (element [:message {:mid (:uuid message)
                                         :from (str room-id "@" domain
                                                    "/" (:nick user))
                                         :type "groupchat"
                                         :to (:jid session)}
                               [:body (:content message)]])))

            ;; This is a flow message
            (let [user (<! (get-resource token "users" user-id))]
              (.send client
                     (element [:message {:mid (:uuid message)
                                         :from (str user-id "@" domain)
                                         :type "chat"
                                         :to (:jid session)}
                               [:body (:content message)]]))
              )))))))

(defn update-streams!
  "Recreates flowdock event listeners for active flows."
  [token id]
  (when id (swap! sessions update-in [token :flows] conj id))
  (let [session (@sessions token)
        stream (:stream session)
        new-stream (.stream (:session session)
                            (to-array (:flows session))
                            (clj->js {:user 1 :active "true"}))]
    (when stream (.end stream))
    (swap! sessions assoc-in [token :stream] new-stream)
    (doto new-stream
      (.on "message"
           #(handle-message session (js->clj % :keywordize-keys true))))))
