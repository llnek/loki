;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.sys.xpis

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.core]
        [czlab.basal.str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def loki-event-type-protected 1)
(def loki-event-type-public 2)
(def loki-event-type-private 3)

;; reply codes
(def loki-event-status-error 500)
(def loki-event-status-ok 200)

;; request codes
(def loki-event-playgame-req 600)
(def loki-event-playreq-ok 601)
(def loki-event-playreq-nok 602)

(def loki-event-joingame-req 651)
(def loki-event-joinreq-ok 652)
(def loki-event-joinreq-nok 653)

;; user and room codes

(def loki-event-user-nok 700)
(def loki-event-game-nok 701)
(def loki-event-room-nok 702)
(def loki-event-room-filled 703)
(def loki-event-rooms-full 704)

;; msg codes

(def loki-event-player-joined 800)
(def loki-event-await-start 801)

(def loki-event-connected 820)
(def loki-event-started 821)
(def loki-event-closed 822)
(def loki-event-tear-down 823)

(def loki-event-restart 840)
(def loki-event-start 841)
(def loki-event-stop 842)
(def loki-event-start-round 843)
(def loki-event-end-round 844)

(def loki-event-poke-rumble 860)
(def loki-event-poke-move 861)
(def loki-event-poke-wait 862)
(def loki-event-sync-arena 863)

(def loki-event-replay 880)
(def loki-event-play-move 881)
(def loki-event-play-scrubbed 882)

(def loki-event-game-won 890)
(def loki-event-game-tie 891)

;; end game
(def loki-event-quit 911)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
;;
(defprotocol GameLogic ;;extends Initable, Startable, Restartable {
  ""
  (get-player-gist [_ id] "")
  (start-round [_ arg] "")
  (end-round [_] "")
  (on-game-event [_ evt] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PubSub
  ""
  (unsubsc-if-session [_ session] "")
  (unsubsc [_ handler] "")
  (subsc [_ handler] "")
  (pub-event [_ event] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol GameRoom
  ""
  (broad-cast [_ evt] "")
  (count-players [_] "")
  (room-open? [_] "")
  (on-room-event [_ evt] ""))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

