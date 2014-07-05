(ns flowdock-xmpp.utils
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.nodejs :as node]
            [cljs.core.async :refer [chan close! >! <! put!]]))


(def ^:private ltx (node/require "ltx"))

(def ^:private node-request (node/require "request"))

(defn element
  "Converts a Clojure vector to a ltx object."
  [root]
  (let [Element (.-Element ltx)
        root (if (not (map? (second root)))
               (into [(first root) {}] (rest root))
               root)
        [tag attrs & children] root
        elm (Element. (clj->js tag) (clj->js attrs))]
    (doseq [child children]
      (.push (.-children elm) (if (vector? child) (element child) child)))
    elm))

(defn get-child [elm]
  (first (filter #(.-getName %) (.-children elm))))

;; From https://gist.github.com/swannodette/6385166

(defn error? [x]
  (instance? js/Error x))

(defn run-task [f & args]
  (let [out (chan)
        cb (fn [err & results]
             (go (if err
                   (>! out err)
                   (>! out results))
                 (close! out)))]
    (apply f (concat args [cb]))
    out))

;; wrap a task to run in a thunk so a supervisor can call it
(defn task [& args]
  (fn [] (apply run-task args)))

;; a policy so a supervisor knows to retry a task
(defprotocol IPolicy
  (-retry? [this err attempts]))

;; retry a task up to N times policy
(defn maxtries [n]
  (reify
    IPolicy
    (-retry? [_ err attempts]
      (< attempts n))))

;; take a task to run and a policy for retries
(defn supervisor [task policy]
  (go (loop [attempts 0 err nil]
        (if-not (-retry? policy err attempts)
          err
          (let [v (<! (task))]
            (if (error? v)
              (let [attempts (inc attempts)]
                (do (.log js/console "Attempt" attempts)
                    (recur attempts v)))
              v))))))

(defn request
  "Performs an HTTP request."
  [url-or-opts]
  (supervisor
   (task node-request (clj->js url-or-opts))
   (maxtries 3)))
