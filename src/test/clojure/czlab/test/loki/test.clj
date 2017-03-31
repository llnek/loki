;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.test.loki.test

  (:require [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.loki.sys.session]
        [czlab.loki.sys.player]
        [czlab.loki.sys.util]
        [czlab.loki.game.core]
        [czlab.loki.game.room]
        [czlab.loki.game.reqs]
        [czlab.loki.net.core]
        [czlab.basal.format]
        [czlab.basal.meta]
        [czlab.basal.core]
        [czlab.basal.str]
        [clojure.test])

  (:import [io.netty.handler.codec.http.websocketx
            WebSocketFrame TextWebSocketFrame]
           [czlab.wabbit.sys Execvisor]
           [czlab.wabbit.ctl Pluglet]
           [czlab.loki.game Game]
           [czlab.loki.sys Room]
           [czlab.basal Stateful Cljrt]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  evt-json
  "{\"type\" : 2, \"status\": 200, \"code\":911, \"body\": { \"a\" : 911 }}")
(def ^:private evt-body {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockDelegate "" ^Game []
  (reify Game
    (playerGist[_ _])
    (onEvent [_ _])
    (restart [_ _])
    (restart [_])
    (init [_ _])
    (start [_ _])
    (start [_])
    (stop [_])
    (startRound [_ _])
    (endRound [_])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockExec "" []
  (let [rts (Cljrt/newrt)
        impl (muble<>)]
    (reify
      Execvisor

      (homeDir [_] )
      (pkeyBytes [this] (bytesit "hello world"))
      (pkey [_] (.toCharArray "hello world"))
      (cljrt [_] rts)

      (getx [_] impl)
      (version [_] "1.0")
      (id [_] "1")

      (uptimeInMillis [_] 0)
      (locale [_] nil)
      (startTime [_] 0)
      (kill9 [_] )
      (start [this _] )
      (stop [this] )
      (acquireDbPool [_ gid] nil)
      (acquireDbAPI [_ gid] nil)
      (dftDbPool [_] nil)
      (dftDbAPI [_] nil)
      (child [_ sid])
      (hasChild [_ sid])
      (core [_] nil)
      (config [_] {})
      (dispose [this]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockPluglet "" ^Pluglet []
  (let [impl (muble<>)
        exe (mockExec)]
    (reify Pluglet
      (isEnabled [this] true)
      (version [_] "1")
      (config [_] {})
      (spec [_] {})
      (server [this] exe)
      (getx [_] impl)
      (hold [_ trig millis])
      (id [_] "?")
      (dispose [_])
      (init [this cfg0])
      (start [this arg])
      (stop [_]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  games-meta
  {:game-1
   {:layout "portrait",
    :name  "Test",
    :description "Fun!",
    :keywords "",
    :height  480,
    :width  320
    :pubdate #inst "2016-01-01"
    :author "joe"
    :network {
      :enabled? true
      :minp 2
      :maxp 2
      :impl  :czlab.test.loki.test/testArena}
    :image "ui/catalog.png"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn testArena "" ^Game [_ _ ] (mockDelegate))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestloki-test

  (is (do->true
        (initGameRegistry! games-meta)))

  (is (let [evt (eventObj<> Events/PUBLIC  Events/QUIT evt-body {:x 7})]
        (and (= 2 (.value ^Events (:type evt)))
             (= Events/OK (:status evt))
             (= 911  (.value ^Events (:code evt)))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [evt (errorObj<> Events/PUBLIC Events/QUIT evt-body {:x 7})]
        (and (= 2 (.value ^Events (:type evt)))
             (= Events/ERROR (:status evt))
             (= 911 (.value ^Events (:code evt)))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> Events/PUBLIC Events/QUIT  evt-body)
                    (encodeEvent ))
            s (.text evt)]
        (and (> (.indexOf s "2") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "911") 0))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> Events/PUBLIC Events/QUIT)
                    (encodeEvent))
            s (.text evt)]
        (and (> (.indexOf s "2") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "911") 0))))

  (is (let [evt (decodeEvent evt-json {:x 3})]
        (and (= Events/PUBLIC (:type evt))
             (= Events/QUIT (:code evt))
             (== 911 (get-in evt [:body :a])))))

  (is (some? (lookupGame "game-1")))

  (is (let [c1 (lookupPlayer "u1" "p1") ;; user#1
            c2 (lookupPlayer "u1")
            c3 (removePlayer "u1")
            c4 (lookupPlayer "u1")]
        (prn!! "POOO === %s" @c1)
        (and (some? c1)
             (identical? c1 c2)
             (some? c3)
             (nil? c4))))

  (is (let [c1 (lookupPlayer "u1" "p1") ;; user#2
            _ (prn!! "ZOOO === %s" @c1)
            _ (.update ^Stateful c1 {:email "e" :name "n"})
            e (:email @c1)
            n (:name @c1)
            nn (:userid @c1)
            id (id?? c1)
            cs (countSessions c1)
            _ (logout c1)
            c4 (lookupPlayer "u1")]
        (and (some? c1)
             (= e "e")
             (= n "n")
             (== 0 cs)
             (= nn "u1")
             (not= nn id)
             (nil? c4))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :settings {:a 1}
                                 :principal  "u1"
                                 :credential "p1"}}) ;user#3
            t (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :settings {:b 2}
                                 :principal  "u2" ;user#4
                                 :credential "p2"}})
            gid (keyword gid)
            r1 (some-> ^Stateful s .deref :room)
            r2 (some-> ^Stateful t .deref :room)
            ok
            (and (some? r1)
                 (some? r2)
                 (identical? r1 r2)
                 (= 1 (:a @s))
                 (= 2 (:b @t))
                 (== 1 (countGameRooms gid))
                 (== 0 (countFreeRooms gid))
                 (not (.canOpen ^Room r1)))
            _ (clearFreeRooms gid)
            _ (clearGameRooms gid)]
        (and ok
             (== 0 (countGameRooms gid))
             (== 0 (countFreeRooms gid)))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :principal  "u3"
                                 :credential "p3"}}) ;user#5
            gid (keyword gid)
            ^Room r (some-> ^Stateful s .deref :room)
            ok
            (and (some? r)
                 (== 1 (countFreeRooms gid))
                 (not (.canOpen r)))
            _ (removeFreeRoom gid (id?? r))]
        (and ok
             (== 0 (countFreeRooms gid)))))

  (is (let [;;_ (clearAllSessions)
            gid "game-1"
            xxx (lookupPlayer "u4" "p4") ;user#6
            yyy (countSessions xxx)
            _ (prn!! "yyyyy = %d" yyy)
            s (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :principal  "u4"
                                 :credential "p4"}})
            _ (prn!! "session===== %s" (dissoc @s :room))
            pu4_ok (lookupPlayer "u4")
            pu4 (:player @s)
            cnt (countSessions pu4)
            ^Room r (some-> ^Stateful s .deref :room)
            na (not (.canOpen r))
            t (doJoinReq {:source (mockPluglet)
                          :body {:roomid (sname (some-> r id??))
                                 :gameid gid
                                 :principal  "u5"
                                 :credential "p5"}})
            gid (keyword gid)
            ^Room r2 (some-> ^Stateful t .deref :room)
            _ (logout pu4)
            cnt2 (countSessions pu4)
            pu4_nok (lookupPlayer "u4")]
        (prn!! "puk_ok = %s" pu4_ok)
(prn!! "puk_nok = %s" pu4_nok)
(prn!! "cnt1 = %d, cnt2 = %d" cnt cnt2)

        (and (some? r)
             (some? r2)
             (= 1 cnt)
             (= 0 cnt2)
             (some? pu4_ok)
             (nil? pu4_nok)
             na
             (identical? r r2)
             (not (.canOpen r2))
             (== 1 (countGameRooms gid))
             (== 0 (countFreeRooms gid))
             (do->true (clearGameRooms gid))
             (do->true (clearFreeRooms gid)))))

  (is (== 90 (rad->deg (deg->rad 90))))

  (is (string? "That's all folks!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

