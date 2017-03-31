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

  (:require [czlab.basal.logging :as log])

  (:use [czlab.convoy.nettio.core]
        [czlab.basal.format]
        [flatland.ordered.map]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.loki.sys.util]
        [czlab.loki.net.core]
        [czlab.loki.sys.session]
        [czlab.loki.game.arena])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.wabbit.ctl Pluglet]
           [io.netty.channel Channel]
           [czlab.jasal Openable]
           [czlab.basal Stateful]
           [czlab.loki.sys Room]
           [clojure.lang Keyword]
           [czlab.loki.net PubSub Events]))

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
(defn removeGameRoom
  "Remove an active room"
  [gameid roomid]

  (locking _room-mutex_
    (when-some [r (-> (@game-rooms gameid)
                      (get roomid))]
      (log/debug "remove room(A): %s, game: %s" roomid gameid)
      (swap! game-rooms update-in [gameid] dissoc roomid)
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  [gameid roomid]

  (locking _room-mutex_
    (when-some [r (-> (@free-rooms gameid)
                      (get roomid))]
      (log/debug "remove room(F): %s, game: %s" roomid gameid)
      (swap! free-rooms update-in [gameid] dissoc roomid)
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addXXXRoom ""
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
(defn lookupFreeRoom
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
(defn- lookupGameRoom
  "" [gameid roomid]
  (log/debug "looking for room(A): %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- newFreeRoom
  "" [game {:keys [source] :as options}]

  (let [p (partial removeGameRoom (id?? game))
        room (defarena game p source)]
    (log/debug "created a new room(F): %s" room)
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
          s (defsession room
                        player
                        (merge arg {:number n}))]
      (swap! (.state ^Stateful room)
             update-in
             [:conns] assoc (id?? s) s)
      (doto s addSession))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn disconnect "Disconnect a player from room"
  [room session]

  (let [{:keys [player]} @session
        {:keys [disp]} @room]
    (swap! (.state ^Stateful room)
           update-in
           [:conns]
           dissoc (id?? session))
    (removeSession session)
    (. ^PubSub disp unsubscribeIfSession session)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  "" [game plyr arg]

  (locking _room-mutex_
    (let [^Room room (or (lookupFreeRoom (id?? game))
                         (newFreeRoom game arg))
          s (get-in arg [:body :settings])
          pss (some-> room (connect plyr s))]
      (when (some? pss)
        (let
          [^Channel ch (:socket arg)
           src {:gameid (sname (id?? game))
                :roomid (id?? room)
                :puid (id?? plyr)
                :pnum (:number @pss)}
           evt (privateEvent<> Events/PLAYREQ_OK src)]
          (. ^Openable pss open arg)
          (setAKey ch PSSN pss)
          (log/debug "replying msg to user: %s" evt)
          (replyEvent ch evt)
          (bcast! room
                  Events/PLAYER_JOINED
                  (select-keys src [:pnum :puid]))
          (if (.canOpen room)
            (do
              (log/debug "room has enough players, can open")
              (log/debug "room.canOpen = true")
              (addGameRoom room)
              (.open ^Openable room _empty-map_))
            (addFreeRoom room))))
      pss)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn joinRoom ""
  [plyr gameid roomid arg]
  {:pre [(some? plyr)]}

  (locking _room-mutex_
    (let [^Room room (or (lookupGameRoom gameid roomid)
                         (lookupFreeRoom gameid roomid))
          s (get-in arg [:body :settings])
          game (some-> ^Stateful room .deref :game)
          ch (:socket arg)]
      (when (and room
                 (< (.countPlayers room)
                    (:maxPlayers @game)))
        (let [pss (connect room plyr s)
              src {:gameid (sname (id?? game))
                   :roomid (id?? room)
                   :puid (id?? plyr)
                   :pnum (:number @pss)}
              evt (privateEvent<> Events/JOINREQ_OK src)]
          (. ^Openable pss open arg)
          (setAKey ch PSSN pss)
          (replyEvent ch evt)
          (log/debug "replying back to user: %s" evt)
          (if (.canOpen room)
            (do
              (log/debug "room has enough players, can open")
              (log/debug "room.canOpen = true")
              (addGameRoom room)
              (.open ^Openable room _empty-map_))
            (addFreeRoom room))
          pss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

