;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.xpis

  (:require [czlab.basal.core :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def type-private 3)
(def type-public 2)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn msg-types

  ""
  [v]

  (cond
    (= v type-public) "public"
    (= v type-private) "private" :else ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def status-error 500)
(def status-ok 200)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn status-codes

  ""
  [v]

  (cond
    (= v status-ok) "OK"
    (= v status-error) "Internal Server Error" :else ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def playgame-req 600)
(def playreq-ok 601)
(def playreq-nok 602)
(def joingame-req 651)
(def joinreq-ok 652)
(def joinreq-nok 653)

(def user-nok 700)
(def game-nok 701)
(def room-nok 702)
(def room-filled 703)
(def rooms-full 704)

(def player-joined 800)
(def await-start 801)

(def connected 820)
(def started 821)
(def closed 822)
(def tear-down 823)

(def restart 840)
(def start 841)
(def stop 842)
(def start-nxt-round 843)
(def end-cur-round 844)

(def poke-rumble 860)
(def poke-move 861)
(def poke-wait 862)
(def sync-arena 863)

(def replay 880)
(def play-move 881)
(def play-scrubbed 882)

(def game-won 890)
(def game-tie 891)

(def quit 911)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn msg-codes

  ""
  [v]

  (cond
    (= v playgame-req) "playgame-req"
    (= v playreq-ok) "playreq-ok"
    (= v playreq-nok) "playreq-nok"
    (= v joingame-req) "joingame-req"
    (= v joinreq-ok) "joinreq-ok"
    (= v joinreq-nok) "joinreq-nok"
    (= v user-nok) "user-nok"
    (= v game-nok) "game-nok"
    (= v room-nok) "room-nok"
    (= v room-filled) "room-filled"
    (= v rooms-full) "rooms-full"
    (= v player-joined) "player-joined"
    (= v await-start) "await-start"
    (= v connected) "connected"
    (= v started) "started"
    (= v closed) "closed"
    (= v tear-down) "tear-down"
    (= v restart) "restart"
    (= v start) "start"
    (= v stop) "stop"
    (= v start-nxt-round) "start-round"
    (= v end-cur-round) "end--round"
    (= v poke-rumble) "poke-rumble"
    (= v poke-move) "poke-move"
    (= v poke-wait) "poke-wait"
    (= v sync-arena) "sync-arena"
    (= v replay) "replay"
    (= v play-move) "play-move"
    (= v play-scrubbed) "play-scrubbed"
    (= v game-won) "game-won"
    (= v game-tie) "game-tie"
    (= v quit) "quit"
    :else
    ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol GameBoard
  ""
  (get-next-moves [_ game] "")
  (game-over? [_ game] "")
  (eval-score [_ game] "")
  (unmake-move [_ game move] "")
  (make-move [_ game move] "")
  (switch-player [_ game] "")
  (take-snap-shot [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol GameImpl
  ""
  (get-player-gist [_ id] "")
  (start-round [_ arg] "")
  (end-round [_] "")
  (on-game-event [_ evt] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol PubSub
  ""
  (unsub-if-session [_ session] "")
  (unsub [_ handler] "")
  (sub [_ handler] "")
  (pub-event [_ event] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol GameRoom
  ""
  (broad-cast [_ evt] "")
  (count-players [_] "")
  (can-open-room? [_] "")
  (on-room-event [_ evt] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

