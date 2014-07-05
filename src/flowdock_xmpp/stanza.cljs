(ns flowdock-xmpp.stanza
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [flowdock-xmpp.flowdock
             :refer [get-resource sessions update-streams!]
             :as flowdock]
            [flowdock-xmpp.iq-get :as iq-get]
            [flowdock-xmpp.common :refer [domain not-implemented jid
                                          update-streams!]]
            [flowdock-xmpp.utils :refer [element get-child]]))

(defmulti handle
  "Handles incoming XMPP stanzas. Stanzas will get dispatched
  as [:name :type] if the stanza contains a type attribute, :name
  otherwise."
  (fn [client stanza]
    (let [name (-> stanza (.-name) keyword)
          type (-> stanza (.-attrs) (.-type) keyword)]
      (if type
        [name type]
        name))))

;; Forwards get queries to iq_get/handle and return the response.
(defmethod handle [:iq :get] [client stanza]
  (go (let [attrs (.-attrs stanza)
            from (.-from attrs)
            to (.-to attrs)
            id (.-id attrs)
            query (get-child stanza)
            child (get-child query)
            xmlns (.-xmlns (.-attrs query))
            response (<! (iq-get/handle
                          {:from from
                           :to to
                           :token (-> from (str/split #"@") first)
                           :client client
                           :query (if child
                                    [xmlns (.-name child)]
                                    xmlns)}))]
        [:iq {:type :result, :from to, :to from, :id id}
         (into [(.-name query) {:xmlns xmlns :ver (.toISOString (js/Date.))}]
               response)])))

;; Handles presence stanzas, which include both room join requests and client
;; status changes.
(defmethod handle :presence [client stanza]
  (let [from (.-from (.-attrs stanza))
        to (.-to (.-attrs stanza))
        room-id (-> to (str/split #"@") first)
        c (.getChild stanza "c")
        node (when c (.-node (.-attrs c)))]
    (if (some #{"#"} room-id)
      ;; if room-id is valid handle this as a join request
      (let [[flow channel] (str/split room-id #"#")]
        (go
          (let [token (-> from (str/split #"@") first)
                path  (str "flows/" flow "/" channel)
                room (<! (get-resource token path))
                buddies (:users room)
                buddy-map (zipmap (map (comp str :id) buddies) buddies)
                buddies (remove :disabled buddies)
                user (<! (get-resource token "user"))
                messages (<! (get-resource token path "messages?limit=80"))
                messages (filter (comp string? :content) messages)]

            (update-streams! token (str flow "/" channel))

            (doseq [buddy buddies]
              (let [self (= (:id user) (:id buddy))
                    res [:presence {:from (str room-id "@" domain "/"
                                               (:nick buddy))
                                    :to from}
                         [:x {:xmlns "http://jabber.org/protocol/muc#user"}
                          [:item {:affiliation "member"
                                  :role "participant"
                                  :jid (if self from (jid buddy))}]
                          (when self [:status {:code "110"}])]]]
                (.send client (element res))))
            (doseq [message messages]
              (let [buddy (-> message :user buddy-map)
                    self (= (:id user) (:id buddy))
                    timestamp (-> message :sent (js/Date.) (.toISOString))]
                (.send client
                       (element [:message {:mid (:uuid message)
                                           :from (str room-id "@" domain
                                                      "/" (:nick buddy))
                                           :type "groupchat"
                                           :to from}
                                 [:body (:content message)]
                                 [:delay {:xmlns "urn:xmpp:delay"
                                          :from_jid (if self from (jid buddy))
                                          :stamp timestamp}]])))))))

      ;; otherwise just set the client presence
      (go [:presence {:from from
                      :to (-> from (str/split #"/") first)}
           [:x {:xmlns "http://www.flowdock.com"}
            [:client_type node]]]))))

;; Handles sending of new private messages.
(defmethod handle [:message :chat] [client stanza]
  (go
    (when-let [body (.getChild stanza "body")]
      (let [token (-> stanza (.-attrs) (.-from) (str/split #"@") first)
            to (-> stanza (.-attrs) (.-to) (str/split #"@") first)
            id (-> stanza (.-attrs) (.-id))
            message (str/join (.-children body))
            delivered [:message {:from (-> stanza (.-attrs) (.-to))
                                 :to (-> stanza (.-attrs) (.-from))}
                       [:x {:xmlns "jabber:x:event"}
                        [:delivered]
                        [:id id]]]]
        (when (-> (flowdock/send-message token message :user to) <! :sent)
          delivered)))))

;; Handles sending of new flow messages.
(defmethod handle [:message :groupchat] [client stanza]
  (let [token (-> stanza (.-attrs) (.-from) (str/split #"@") first)
        to (-> stanza (.-attrs) (.-to) (str/split #"@") first)
        [org flow] (str/split to #"#")
        id (-> stanza (.-attrs) (.-id))
        body (.getChild stanza "body")
        message (str/join (.-children body))
        user (-> token (@sessions) :user)]
    (go (let [response (<! (flowdock/send-message
                            token message :flow flow :org org))]
          (when (:sent response)
            [:message {:from (str (-> stanza (.-attrs) (.-to))
                                  "/" (:nick user))
                       :to (-> stanza (.-attrs) (.-from))
                       :type "groupchat"
                       :id id}
             [:body message]])))))

(defmethod handle :default [client _] (go not-implemented))
