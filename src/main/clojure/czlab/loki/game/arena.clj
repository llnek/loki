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

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.jasal
            Restartable
            Startable
            Initable
            Idable
            Openable
            Receivable
            Sendable
            Dispatchable]
           [java.io Closeable]
           [czlab.loki.game Game]
           [czlab.loki.sys Room]
           [czlab.wabbit.ctl Pluglet]
           [czlab.loki.net Events PubSub]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtStartBody [^Game impl sessions]
  (preduce<map>
    #(let [{:keys [number player]}
           (deref %2)
           yid (id?? player)
           g (.playerGist impl yid)]
       (assoc! %1
               yid
               (merge {:pnum number} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defentity Arena
  Openable
  (open [me _]
    (let [{:keys [disp source conns game]}
          @data
          rt (.. ^Pluglet
                  source
                  server cljrt)
          sss (sort-by #(:created (deref %))
                       (vals conns))
          g (.callEx rt
                     (strKW (:implClass @game))
                     (vargs* Object me sss))]
          (log/debug "activating room %s" me)
          (doseq [s sss]
            (. ^PubSub disp subscribe (defsubr s)))
          (swap! data
                 assoc
                 :impl g :latch conns
                 :starting? false
                 :opened? true :active? false)
          (. ^Initable g init _empty-map_)
          (bcast! me Events/START (fmtStartBody g sss))))
  (close [me]
    (log/debug "closing arena [%s]" me)
    (doseq [[_ s] (:conns @data)]
      (doto s removeSession closeQ))
    (swap! data assoc :conns _empty-map_)
    ((:finz @data) (id?? me)))
  Idable
  (id [_] (:id @data))
  Object
  (toString [me] (sname (id?? me)))
  Restartable
  (restart [me _]
    (log/debug "arena#restart() called")
    (->> (-> (:impl @data)
             (fmtStartBody (vals (:conns @data))))
         (bcast! me Events/RESTART)))
  (restart [_] (.restart _ _empty-map_))
  Startable
  (start [_ arg]
    (log/debug "arena#start called")
    (swap! data assoc :active? true)
    (. ^Startable (:impl @data) start arg))
  (start [_] (.start _ _empty-map_))
  (stop [_]
    (swap! data assoc :active? false))
  Room
  (countPlayers [_] (count (:conns @data)))
  (broadcast [_ evt]
    (. ^PubSub (:disp @data) publish evt))
  (canOpen [me]
    (and (not (:opened? @data))
         (>= (.countPlayers me)
             (:minPlayers @(:game @data)))))
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
        (let [rc (. ^Game impl onEvent evt)]
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
(defmacro defarena "" [game finzer source]
  `(entity<> Arena {:numctr (AtomicInteger.)
                    :disp (defdispatcher)
                    :id (keyword (uid<>))
                    :shutting? false
                    :opened? false
                    :active? false
                    :source ~source
                    :finz ~finzer
                    :game ~game
                    :conns {} }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


