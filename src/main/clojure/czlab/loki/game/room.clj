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
        [czlab.basal.io]
        [czlab.loki.sys.util]
        [czlab.loki.net.core]
        [czlab.loki.net.disp]
        [czlab.loki.game.arena]
        [czlab.loki.sys.session])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [java.util.concurrent CountDownLatch]
           [czlab.loki.sys Room Player Session]
           [czlab.jasal Identifiable Sendable Dispatchable]
           [czlab.loki.game GameMeta GameRoom]
           [czlab.wabbit.ctl Pluglet]
           [io.netty.channel Channel]
           [clojure.lang Keyword]
           [czlab.loki.net Events Subr PubSub]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _room_mutex_ (Object.))

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
  "" [gameid] (count (get @free-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countGameRooms
  "" [gameid] (count (get @game-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearFreeRooms
  "Internal only" {:no-doc true}
  [gameid]
  (locking _room_mutex_
    (swap! free-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearGameRooms
  "Internal only" {:no-doc true}
  [gameid]
  (locking _room_mutex_
    (swap! game-rooms assoc gameid (ordered-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  ^GameRoom [gameid roomid]

  (locking _room_mutex_
    (let [gm (@game-rooms gameid)
          r (get gm roomid)]
      (when (some? r)
        (log/debug "remove room(A): %s, game: %s" roomid gameid)
        (swap! game-rooms update-in [gameid] dissoc roomid)
        r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  ^GameRoom [gameid roomid]

  (locking _room_mutex_
    (let [gm (@free-rooms gameid)
          r (get gm roomid)]
      (when (some? r)
        (log/debug "remove room(F): %s, game: %s" roomid gameid)
        (swap! free-rooms update-in [gameid] dissoc roomid)
        r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addFreeRoom
  "Add a new partially filled room
   into the pending set"
  ^GameRoom [^GameRoom room] {:pre [(some? room)]}

  (locking _room_mutex_
    (let [rid (.id room)
          gid (.. room game id)
          m (@free-rooms gid)]
      (log/debug "adding a room(F): %s, game: %s" rid gid)
      (if (some? m)
        (swap! free-rooms update-in [gid] assoc rid room)
        (swap! free-rooms assoc gid (ordered-map rid room)))
      room)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addGameRoom
  "Move room into the active set"
  ^GameRoom [^GameRoom room] {:pre [(some? room)]}

  (let [rid (.id room)
        gid (.. room game id)
        m (@game-rooms gid)]
    (log/debug "add a room(A): %s, game: %s" rid gid)
    (if (some? m)
      (swap! game-rooms update-in [gid] assoc rid room)
      (swap! game-rooms assoc gid (ordered-map rid room)))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- detachFreeRoom
  "" [gameid roomid]

  (log/debug "found a room(F): %s, game: %s" roomid gameid)
  (swap! free-rooms
         update-in [gameid] dissoc roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupFreeRoom
  "Returns a free room which is detached from the pending set"
  {:tag GameRoom}

  ([gameid roomid]
   (let [gm (@free-rooms gameid)
         ^GameRoom r (get gm roomid)]
     (when (some? r)
       (detachFreeRoom gameid (.id r))
       r)))

  ([gameid]
   (let [gm (@free-rooms gameid)]
    (when-some
      [^GameRoom r (first (vals gm))]
      (detachFreeRoom gameid (.id r))
      r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- lookupGameRoom
  "" ^GameRoom [gameid roomid]
  (log/debug "looking for room: %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- localSubr<>
  "" ^Subr [^Session ps]

  (reify Subr
    (eventType [_] Events/PUBLIC)
    (session [_] ps)
    (receive [me evt]
      (if (== (.eventType me)
              (:type evt))
        (.send ps evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- gameRoom<>
  "" ^GameRoom [^GameMeta gameObj {:keys [source]}]

  (let [impl (muble<> {:shutting? false})
        ctr (.server ^Pluglet source)
        pcount (AtomicInteger.)
        crt (.cljrt ctr)
        sessions (atom {})
        disp (dispatcher<>)
        rid (uid<>)
        created (now<>)]
    (reify GameRoom

      (countPlayers [_] (count @sessions))

      (disconnect [_ ps]
        (let [py (.player ps)]
          (swap! sessions dissoc (.id ps))
          (.removeSession py ps)
          (.unsubscribeIfSession disp ps)))

      (connect [this py]
        (let [ps (session<> this
                            py
                            (.incrementAndGet pcount))
              _ {:puid (.id py)
                 :pnum (.number ps)}]
          (swap! sessions assoc (.id ps) ps)
          (.addSession py ps)
          ps))

      (isShuttingDown [_] (boolean (.getv impl :shutting?)))
      (isOpen [_] (boolean (.getv impl :active?)))
      (canOpen [this]
        (and (not (.isOpen this))
             (>= (.countPlayers this)
                 (.minPlayers gameObj))))

      (arena [_] (.getv impl :arena))
      (game [_] gameObj)
      (id [_] rid)

      (close [_]
        (doseq [^Session v (vals @sessions)]
          (-> (.player v)
              (.removeSession v))
          (closeQ v))
        (reset! sessions {}))

      (open [this]
        (let [sss (sort-by #(.number ^Session %)
                           (vals @sessions))
              sc (count sss)
              a (->> (.callEx crt
                             (strKW (.implClass gameObj))
                             (vargs* Object this sss))
                     (arena<> this ))]
          (log/debug "activating room %s" rid)
          (doto impl
            (.setv :active? true)
            (.setv :arena a))
          (doseq [s sss]
            (.addHandler this (localSubr<> s)))
          (.init a sss)))

      (removeHandler [_ h] (.unsubscribe disp h))
      (addHandler [_ h] (.subscribe disp h))

      (broadcast [_ evt] (.publish disp evt))

      (send [this msg]
        (cond
          (isPrivate? msg) (some-> ^Sendable
                                   (:context msg) (.send msg))
          (isPublic? msg) (.broadcast this msg)))

      (receive [this evt]
        (when (and (.isOpen this)
                   (some? (.arena this)))
          (log/debug "room got an event %s" evt)
          (cond
            (isPublic? evt)
            (.broadcast this evt)

            (isPrivate? evt)
            (.update (.arena this) evt))))

      Object

      (hashCode [this] (.hashCode rid))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.id ^Room obj)
                      (.id this)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- newFreeRoom
  "" ^GameRoom [^GameMeta game options]

  (let [room (gameRoom<> game options)]
    (log/debug "created a new play room: %s" (.id room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  "" ^Session [^GameMeta game ^Player plyr arg]

  (locking _room_mutex_
    (let [room (or (lookupFreeRoom (.id game))
                   (newFreeRoom game arg))
          ^Session
          pss (some-> room (.connect plyr))]
      (when (some? room)
        (let
          [^Channel ch (:socket arg)
           src {:puid (.id plyr)
                :room (.id room)
                :game (.id game)
                :pnum (.number pss)}
           evt (privateEvent<> Events/PLAYREQ_OK src)]
          (.bind pss arg)
          (setAKey ch PSSN pss)
          (log/debug "replying back to user: %s" evt)
          (replyEvent ch evt)
          (->> (publicEvent<> Events/PLAYER_JOINED
                              {:pnum (.number pss)
                               :puid (.id plyr)})
               (.broadcast room))
          (if (.canOpen room)
            (do
              (log/debug "room has enough players, can open")
              (addGameRoom room)
              (log/debug "room.canOpen = true")
              (.open room))
            (addFreeRoom room))))
      pss)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn joinRoom
  "" ^Session
  [^Player plyr gameid roomid arg]
  {:pre [(some? plyr)]}

  (locking _room_mutex_
    (let [^GameRoom
          room (or (lookupGameRoom gameid roomid)
                   (lookupFreeRoom gameid roomid))
          ^GameMeta
          game (some-> room .game)
          ch (:socket arg)]
      (when (and room
                 (< (.countPlayers room)
                    (.maxPlayers game)))
        (let [pss (.connect room plyr)
              src {:puid (.id plyr)
                   :room (.id room)
                   :game (.id game)
                   :pnum (.number pss)}
              evt (privateEvent<> Events/JOINREQ_OK src)]
          (.bind pss arg)
          (setAKey ch PSSN pss)
          (replyEvent ch evt)
          (log/debug "replying back to user: %s" evt)
          (when-not (.isOpen room)
            (if (.canOpen room)
              (do
                (log/debug "room has enough players, can open")
                (addGameRoom room)
                (log/debug "room.canOpen = true")
                (.open room))
              (addFreeRoom room)))
          pss)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

