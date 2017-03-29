;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.sys.session

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.process]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.loki.sys.util]
        [czlab.loki.net.core]
        [czlab.loki.net.disp])

  (:import [czlab.loki.sys Room Player Session]
           [io.netty.channel Channel]
           [czlab.jasal Hierarchial]
           [czlab.loki.net Events]
           [czlab.loki.net MsgSender]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private sessions-db (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Session [data]
  Identifiable
  (id [_] (:id data))
  Stateful
  (state [_] data)
  Object
  (hashCode [me] (.hashCode (.id me)))
  (toString [me] (.id me))
  (equals [me obj]
    (if (nil? obj)
      false
      (or (identical? me obj)
          (and (= (.getClass me)
                  (.getClass obj))
               (= (.id ^Identifiable obj)
                  (.id me)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro defsession "" [room player settings]
  (let [sid (jid<>)]
    `(Session. (atom
                 (merge settings
                        {:shutting? false
                         :status false
                         :parent nil
                         :tcp nil
                         :created (now<>)
                         :id sid :room room :player player})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn create "" [room player settings]
  (let [s (defsession room player settings)]
    (swap! sessions-db assoc (.id s) s)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn send! [ss msg]
  (let [{tcp shutting? status} (.state ss)]
    (if (and status
             (not shutting?))
      (-> ^MsgSender tcp (.send msg)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn connect [ss options]
  (swap! (.state ss)
         assoc
         :status true
         :parent (:source options)
         :tcp (tcpSender<> (:socket options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn close "" [ss]
  (let [{:keys [tcp] :as s} (.state ss)]
    (when (:status s)
      (closeQ tcp)
      (swap! s
             assoc
             :tcp nil
             :status false ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addTo "Add a session to this player"
  [{:keys [id] :as player} session]
  (swap! @sessions-db
         update-in
         [id]
         assoc
         (.id ^Identifiable session) session))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn purgeAll
  "Delete all sessions for this player"
  [{:keys [id] :as player}]

  (let [m (@sessions-db id)]
    (swap! @sessions-db dissoc id)
    (doall
      (map #(closeQ %) m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

