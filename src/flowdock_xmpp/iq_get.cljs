(ns flowdock-xmpp.iq-get
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [flowdock-xmpp.flowdock
             :refer [get-resource sessions]
             :as flowdock]
            [flowdock-xmpp.common :refer [domain not-implemented jid]]
            [flowdock-xmpp.utils :refer [element error?] :as utils]))

(defmulti handle
  "Handles incoming `[:iq {:type \"get\"}]` queries."
  :query)

(defn- presence
  "Returns a presence element for user."
  [to user]
  (let [msecs (- (.getTime (js/Date.)) (:last_activity user))]
    (element [:presence {:to to
                         :from (jid user)}
              (when (> msecs 600000) [:show "away"])])))

(defn- conference
  "Returns a conference element bookmark for flow."
  [flow]
  (let [id (:parameterized_name flow)
        org (:organization flow)
        org-name (:name org)
        org-id (:parameterized_name org)
        name (str (:name flow) " (" org-name ")")
        jid (str org-id "#" id "@" domain)]
    [:conference {:name name, :jid jid}
     [:nick "ozan"]]))

;; Sends the presence presence of each buddy and responds with the list of
;; buddies the user has.
(defmethod handle "jabber:iq:roster" [{:keys [from token client]}]
  (go (let [buddies (<! (flowdock/get-buddies token))
            buddies (remove :disabled buddies)
            user-id (-> token (@sessions) :user :id)
            buddies (filter #(not= (:id %) user-id) buddies)]
        (js/setTimeout #(doseq [buddy buddies]
                          (.send client (presence from buddy)))
                       0)
        (map (fn [buddy]
               [:item {:subscription :both
                       :name (:nick buddy)
                       :jid (jid buddy)}])
             buddies))))

;; Responds with a storage element which contains bookmarks for every flow the
;; user has access to.
(defmethod handle ["jabber:iq:private" "storage"] [{:keys [token]}]
  (go (let [response (<! (get-resource token "flows" "all"))
            conferences (when response (map conference response))]
        [(into [:storage {:xmlns "storage:bookmarks"}] conferences)])))

;; Responds with server info.
(defmethod handle "http://jabber.org/protocol/disco#info" [_]
  (go [:identity {:category :server, :type :im, :name "Flowdock"}]))

;; Responds with a vcard element for a user.
(defmethod handle "vcard-temp" [{:keys [token to] :as query}]
  (go (let [user-id (-> to (str/split #"@") first)
            user (if (= user-id token)
                   (-> token (@sessions) :user)
                   (<! (get-resource token "users" user-id)))
            photo (<! (utils/request {:url (:avatar user) :encoding "base64"}))
            content-type (when-not (error? photo)
                           (aget (.-headers (first photo)) "content-type"))]
        [[:FN (:name user)]
         (when-not (error? photo) [:PHOTO
                                   [:TYPE content-type]
                                   [:BINVAL (second photo)]])
         [:NICKNAME (:nick user)]
         [:EMAIL [:USERID (:email user)]]])))

(defmethod handle :default [_] (go not-implemented))
