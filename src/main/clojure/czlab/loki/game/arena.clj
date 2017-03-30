;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.arena

  (:require [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.loki.net.core]
        [czlab.loki.net.disp]
        [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.sys.session])

  (:import [czlab.jasal Idable Receivable Sendable Dispatchable]
           [java.util.concurrent.atomic AtomicInteger]
           [java.io Closeable]
           [czlab.loki.game Game Info Arena]
           [czlab.loki.sys Session]
           [czlab.wabbit.ctl Pluglet]
           [czlab.loki.net Events Subr PubSub]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Arena
  Openable
  (open [me]
    (let [{:keys [dispatcher conns gameInfo]}
          @data
          sss (sort-by #(:number (deref %)) (vals conns))
          g (.callEx crt
                     (strKW (:implClass @gameInfo))
                     (vargs* Object me sss))]
          (log/debug "activating room %s" me)
          (doseq [s sss]
            (.subscribe ^PubSub dispatcher (defsubr s)))
          (swap! data
                 assoc
                 :impl g :latch conns
                 :opened? true :active? false)
          (.init ^Initable g _empty-map)
          (bcast! me Events/START (fmtStartBody g sss))))
  (close [me]
    (log/debug "closing arena [%s]" me)
    (doseq [[k s] (:conns @data)]
      (removeSession s)
      (closeQ s))
    (swap! data assoc :conns {})
    (finzer rid))
  Idable
  (id [_] (:id @data))
  Object
  (hashCode [_] (.hashCode (:id @data)))
  (equals [this obj] (objEQ? this obj))
  (toString [me] (:id @data))
  Restartable
  (restart [me _]
    (log/debug "arena#restart() called")
    (->> (-> (:impl @data)
             (fmtStartBody (vals (:conns @data))))
         (bcast! me Events/RESTART)))
  (restart [_] (.restart _ nil))
  Startable
  (start [_ arg]
    (log/debug "arena#start called")
    (swap! data assoc :active? true)
    (. ^Startable (:impl @data) start arg))
  (start [_] (.start _ nil))
  (stop [_]
    (swap! data assoc :active? false))
  Room
  (countPlayers [_] (count (:conns @data)))
  (broadcast [_ evt]
    (. ^Dispatcher (:dispatcher @data) publish evt))
  (canOpen [me]
    (and (not (:active? @data))
         (>= (.countPlayers me)
             (.minPlayers (:gameInfo @data)))))
  (onEvent [me evt]
    (let [{:keys [context body]} evt
          {:keys [impl latch conns active?]} @data]
      (assert (some? context))
      (cond
        (and (not active?) (isCode? Events/REPLAY evt))
        (locking me
          (when (and (not (:starting? @data))
                     (empty? (:latch @data)))
            (swap! data assoc :latch conns :starting true)
            (.restart me)))

        (and (not active?) (isCode? Events/STARTED evt))
        (if (in? latch (:id @context))
          (locking me
            (log/debug "latch: drop-off: %s" context)
            (swap! data update-in [:latch] dissoc (:id @context))
            (if (empty? (:latch @data))
              (. ^Startable me start (readJsonStrKW body)))))

        (and active? (some? latch) (empty? latch))
        (let [rc (. ^Receivable impl receive evt)]
          (when (and (isQuit? evt)
                     (= rc Events/TEAR_DOWN))
            (bcast! me
                    Events/PLAY_SCRUBBED
                    {:pnum (:number @context)})
            (pause 1000)
            (. ^Closeable me close))))))
  Sendable
  (send [me msg]
    (cond
      (isPrivate? msg) (some-> ^Sendable
                               (:context msg) (.send msg))
      (isPublic? msg) (.broadcast me msg)))
  Receivable
  (receive [me evt]
    (when (:active? @data)
      (log/debug "room recv'ed msg %s" evt)
      (cond
        (isPublic? evt) (.broadcast me evt)
        (isPrivate? evt) (.onEvent me evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtStartBody [^Game impl sessions]
  (preduce<map>
    #(let [^Session s %2
           sn (.number s)
           y (.player s)
           yid (.id y)
           g (.playerGist impl yid)]
       (assoc! %1
               (keyword yid)
               (merge {:pnum sn} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro defarena "" [gameInfo finzer]
  (let [rid (str "arena#" (seqint2))]
    `(entity<> Arena {:numctr (AtomicInteger.)
                      :shutting? false
                      :opened? false
                      :conns {}
                      :active? false
                      :id ~rid})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn connect "" [^Stateful room ^Stateful player arg]
  (let [{:keys [conns numctr]}
        @room
        n (. ^AtomicInteger
             numctr
             incrementAndGet)
        s (defconn room
                   player
                   (merge arg
                          {:number n}))]
    (swap! (.state room)
           update-in
           [:conns] assoc (.id s) s)
    (doto s addSession)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn disconnect "" [^Stateful room ^Stateful session]
  (let [{:keys [player]} @session
        {:keys [dispatcher]} @room]
    (swap! (.state room)
           update-in
           [:conns]
           dissoc (.id ^Idable session))
    (removeSession session)
    (unsubscribeIfSession dispatcher session)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


