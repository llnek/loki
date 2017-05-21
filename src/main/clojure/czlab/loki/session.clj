;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.session

  (:require [czlab.basal.log :as log]
            [czlab.convoy.core :as cc]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s]
            [czlab.basal.io :as i]
            [czlab.loki.util :as u]
            [czlab.loki.net.core :as nc])

  (:import [czlab.jasal Openable Sendable Idable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private sessions-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/decl-mutable GameSession
  Openable
  (open [me options]
    (c/copy* me
             (merge {:status true}
                    (select-keys options
                                 [:source :socket]))))
  (close [me]
    (i/closeQ (:socket @me))
    (c/copy* me
             {:status false :socket nil}))
  Idable
  (id [me] (:id @me))
  Sendable
  (send [me msg]
    (let [{:keys [socket shutting? status]} @me]
      (if (and status
               (not shutting?))
        (some-> socket
                (cc/send-ws-string (nc/encodeEvent msg)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn session<> "" [room player settings]
  (let [sid (s/toKW "sess#" (c/seqint2))
        pid (c/id?? player)
        s (c/mutable<> GameSession
                       (merge settings
                              {:roomid (c/id?? room)
                               :shutting? false
                               :created (c/now<>)
                               :status false
                               :source nil
                               :socket nil
                               :id sid
                               :player player}))]
    (c/doto->> s
               (swap! sessions-db
                      update-in [pid] assoc (c/id?? s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearAllSessions "" [] (reset! sessions-db {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countSessions "" [player]
  (if player
    (count (@sessions-db (c/id?? player))) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeSession
  ""
  [session]
  {:pre [(some? session)]}
  (c/do->nil
    (if-some [p (:player @session)]
      (swap! sessions-db
             update-in
             [(c/id?? p)]
             dissoc
             (c/id?? session)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addSession
  ""
  [session]
  {:pre [(some? session)]}
  (if-some [p (:player @session)]
    (swap! sessions-db
           update-in
           [(c/id?? p)]
           assoc (c/id?? session) session))
  session)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeSessions
  ""
  [player]
  {:pre [(some? player)]}
  (c/do->nil
    (if-some [pid (c/id?? player)]
      (let [m (@sessions-db pid)]
        (swap! sessions-db dissoc pid)
        (doseq [[_ c] m] (i/closeQ c))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

