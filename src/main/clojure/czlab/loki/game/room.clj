;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.room

  (:require [czlab.loki.xpis :as loki :refer :all]
            [czlab.basal.logging :as log])

  (:use [flatland.ordered.map]
        [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.convoy.core]
        [czlab.wabbit.xpis]
        [czlab.loki.util]
        [czlab.loki.session]
        [czlab.loki.net.core]
        [czlab.loki.game.arena])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.jasal Openable]
           [clojure.lang Keyword]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _room-mutex_ (Object.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; {game-id -> map
;;             { room-id -> room }}
(def ^:private free-rooms (atom (ordered-map)))
(def ^:private game-rooms (atom (ordered-map)))
(def ^:private vacancy 1000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countFreeRooms
  "" ^long [gameid] (count (get @free-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countGameRooms
  "" ^long [gameid] (count (get @game-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearFreeRooms
  "Internal only"
  {:no-doc true}
  [gameid]
  (locking _room-mutex_
    (swap! free-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearGameRooms
  "Internal only"
  {:no-doc true}
  [gameid]
  (locking _room-mutex_
    (swap! game-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- removeXXXRoom
  ""
  [gameid roomid db dbtxt]
  (locking _room-mutex_
    (when-some [r (-> (@db gameid)
                      (get roomid))]
      (log/debug "remove %s: %s, game: %s" dbtxt roomid gameid)
      (swap! db update-in [gameid] dissoc roomid)
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  [gameid roomid]
  (removeXXXRoom gameid roomid game-rooms "room(A)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  [gameid roomid]
  (removeXXXRoom gameid roomid free-rooms "room(F)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addXXXRoom
  ""
  [room db dbt]
  {:pre [(some? room)]}
  (locking _room-mutex_
    (let [gid (id?? (:game @room))
          rid (id?? room)
          m? (in? @db gid)]
      (log/debug "adding a %s: %s, game: %s" dbt rid gid)
      (if m?
        (swap! db update-in [gid] assoc rid room)
        (swap! db assoc gid (ordered-map rid room)))
      room)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addFreeRoom
  "Add a new partially filled room into the pending set"
  [room]
  (addXXXRoom room free-rooms "room(F)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addGameRoom
  "Move room into the active set"
  [room]
  (addXXXRoom room game-rooms "room(A)"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- detachFreeRoom "" [gameid roomid]
  (log/debug "%s: %s, game: %s"
             "found a room(F)" roomid gameid)
  (swap! free-rooms update-in [gameid] dissoc roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getFreeRoom
  "Returns a free room which is detached from the pending set"

  ([gameid roomid]
   (locking _room-mutex_
     (when-some [r (get (@free-rooms gameid) roomid)]
       (detachFreeRoom gameid (id?? r))
       r)))

  ([gameid]
   (locking _room-mutex_
     (when-some [[_ r] (first (@free-rooms gameid))]
       (detachFreeRoom gameid (id?? r))
       r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupFreeRoom
  "" [gameid roomid]
  (log/debug "looking for room(F): %s, game: %s" roomid gameid)
  (get (@free-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGameRoom
  "" [gameid roomid]
  (log/debug "looking for room(A): %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- newFreeRoom
  "" [game {:keys [source] :as options}]

  (let [p (partial removeGameRoom (id?? game))
        room (arena<> game p source)]
    (log/debug "created a new room(F): %s" (id?? room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn connect
  "Connect a player to a romm"
  [room player arg]
  (locking room
    (let [{:keys [conns numctr]}
          @room
          n (. ^AtomicInteger
               numctr
               incrementAndGet)
          s (session<> room
                       player
                       (merge arg {:number n}))]
      (setf! room
             :conns
             (assoc conns (id?? s) s))
      (doto s addSession))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn disconnect
  "Disconnect a player from room"
  [room session]
  (let [{:keys [player]} @session
        {:keys [conns disp]} @room]
    (setf! room
           :conns
           (dissoc conns (id?? session)))
    (removeSession session)
    (unsubsc-if-session disp session)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  "" [game plyr arg]

  (locking _room-mutex_
    (let [room (or (getFreeRoom (id?? game))
                         (newFreeRoom game arg))
          s (get-in arg [:body :settings])
          pss (some-> room (connect plyr s))]
      (when (some? pss)
        (let
          [ch (:socket arg)
           src {:gameid (sname (id?? game))
                :roomid (id?? room)
                :puid (id?? plyr)
                :pnum (:number @pss)}
           evt (privateEvent<> ::loki/playreq-ok src)]
          (.open ^Openable pss arg)
          (set-socket-attr ch RMSN {:room room :session pss})
          (log/debug "replying msg to user: %s" (prettyEvent evt))
          (replyEvent ch evt)
          (bcast! room
                  ::loki/player-joined
                  (select-keys src [:pnum :puid]))
          (if (can-open-room? room)
            (do
              (log/debug "room has enough players, can open")
              (log/debug "room.canOpen = true")
              (addGameRoom room)
              (.open ^Openable room))
            (addFreeRoom room))))
      pss)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn joinRoom ""
  [plyr gameid roomid arg]
  {:pre [(some? plyr)]}

  (locking _room-mutex_
    (let [room (or (lookupGameRoom gameid roomid)
                         (getFreeRoom gameid roomid))
          s (get-in arg [:body :settings])
          game (:game (some-> room deref))
          ch (:socket arg)]
      (when (and room
                 (< (count-players room)
                    (:maxPlayers game)))
        (let [pss (connect room plyr s)
              src {:gameid (sname (id?? game))
                   :roomid (id?? room)
                   :puid (id?? plyr)
                   :pnum (:number @pss)}
              evt (privateEvent<> ::loki/joinreq-ok src)]
          (. ^Openable pss open arg)
          (set-socket-attr ch RMSN {:room room :session pss})
          (replyEvent ch evt)
          (log/debug "replying back to user: %s" (prettyEvent evt))
          (if (can-open-room? room)
            (do
              (log/debug "room has enough players, can open")
              (log/debug "room.canOpen = true")
              (addGameRoom room)
              (.open ^Openable room _empty-map_))
            (addFreeRoom room))
          pss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

