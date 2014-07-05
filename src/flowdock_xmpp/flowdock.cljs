(ns flowdock-xmpp.flowdock
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as str]
            [cljs.nodejs :as node]
            [cljs.core.async :refer [chan put! <!] :as async]
            [flowdock-xmpp.utils :refer [request error?]]))

(def Session (.-Session (node/require "flowdock")))
(def sessions (atom {}))

(def api-url "https://api.flowdock.com/")

(def denied "HTTP Basic: Access denied.\n")

(defn get-resource
  "Gets a flowdock resource using the supplied API token and returns a
  map containing the resource data."
  [token & resource]
  (go (let [response (<! (request {:uri (str api-url (str/join "/" resource))
                                   :auth {:user token
                                          :pass "foobar"
                                          :sendImmediately true}
                                   :json true}))]
        (if-not (error? response)
          (-> response second (js->clj :keywordize-keys true))))))

(defn send-message
  "Sends a message to a flow and returns a map containing the message info"
  [token message &{:keys [user flow org]}]
  (go (let [uri (if user
                  (str api-url "/private/" user "/messages")
                  (str api-url "/flows/" org "/" flow "/messages"))
            response (<! (request {:uri uri
                                   :method "POST"
                                   :auth {:user token
                                          :pass "foobar"
                                          :sendImmediately true}
                                   :json {:event "message"
                                          :content message}}))]
        (if-not (error? response)
          (-> response second (js->clj :keywordize-keys true))))))

(defn get-buddies
  "Returns the list of buddies the user associated with the given api
  token has. Instead of retrieving `/users` resource we crawl through
  every flow the user has access to because otherwise we can't
  determine the away status of buddies."
  [token]
  (go (->> (get-resource token "flows" "all")
           <!
           (map #(str (str/replace (:url %) api-url "") "/users"))
           (map (partial get-resource token))
           (async/map vector)
           <!
           (apply concat)
           (group-by :id)
           (map (fn [[id user]] (apply max-key :last_ping user))))))

(defn get-flow
  "Retrieves a flow's info using a node-flowdock's session object."
  [session flow-id]
  (let [out (chan)]
    (.flows session (fn [flows]
                      (let [flow (first (filter #(= (.-id %) flow-id) flows))]
                        (when flow
                          (put! out (js->clj flow :keywordize-keys true))))))
    out))

(defn new-session
  "Returns a new node-flowdock session object for given API token
  which we use for easy access to Flowdock's streaming API."
  [token]
  (Session. token))
