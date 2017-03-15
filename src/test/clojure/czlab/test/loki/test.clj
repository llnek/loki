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

  (:use [czlab.loki.core.session]
        [czlab.loki.core.player]
        [czlab.loki.game.core]
        [czlab.loki.game.room]
        [czlab.loki.game.reqs]
        [czlab.loki.net.core]
        [czlab.basal.format]
        [czlab.basal.meta]
        [czlab.basal.core]
        [czlab.basal.str]
        [clojure.test])

  (:import [czlab.loki.game GameRoom GameImpl]
           [io.netty.handler.codec.http.websocketx
            WebSocketFrame TextWebSocketFrame]
           [czlab.wabbit.sys Execvisor]
           [czlab.wabbit.ctl Pluglet]
           [czlab.loki.core Room]
           [czlab.basal Cljrt]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:private
  evt-json
  "{\"type\" : 100, \"status\": 200, \"code\":111, \"body\": { \"a\" : 911 }}")
(def ^:private evt-body {:a 911})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockDelegate "" ^GameImpl []
  (reify GameImpl
    (onEvent [_ _ _])
    (restart [_ _])
    (start [_ _])
    (stop [_])
    (startRound [_ _])
    (endRound [_])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockExec "" []
  (let [rts (Cljrt/newrt)
        impl (muble<>)]
    (with-meta
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
        (dispose [this]))
      {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mockPluglet "" ^Pluglet []
  (let [impl (muble<>)
        exe (mockExec)]
    (with-meta
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
        (stop [_]))
      {:typeid ::dummy})))

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
    :author "llnek"
    :network {
      :enabled true
      :minp 2
      :maxp 2
      :impl  :czlab.test.loki.test/testArena}
    :status true
    :uri "/arena/test"
    :image "ui/catalog.png"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn testArena "" ^GameImpl [_ _] (mockDelegate))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest czlabtestloki-test

  (is (do->true
        (initGameRegistry! games-meta)))

  (is (let [evt (eventObj<> 100 111 evt-body {:x 7})]
        (and (== 100 (:type evt))
             (== Events/OK (:status evt))
             (== 111 (:code evt))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [evt (errorObj<> 100 111 evt-body {:x 7})]
        (and (== 100 (:type evt))
             (== Events/ERROR (:status evt))
             (== 111 (:code evt))
             (map? (:body evt))
             (== 7 (:x evt)))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> 100 111 evt-body)
                    (encodeEvent ))
            s (.text evt)]
        (and (> (.indexOf s "100") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "111") 0))))

  (is (let [^TextWebSocketFrame
            evt (-> (eventObj<> 100 111)
                    (encodeEvent))
            s (.text evt)]
        (and (> (.indexOf s "100") 0)
             (> (.indexOf s "200") 0)
             (> (.indexOf s "111") 0))))

  (is (let [evt (decodeEvent evt-json {:x 3})]
        (and (== 100 (:type evt))
             (== 200 (:status evt))
             (== 111 (:code evt))
             (== 911 (get-in evt [:body :a])))))

  (is (some? (lookupGame "game-1")))

  (is (let [c1 (lookupPlayer "u1" "p1")
            c2 (lookupPlayer "u1")
            c3 (removePlayer "u1")
            c4 (lookupPlayer "u1")]
        (and (some? c1)
             (identical? c1 c2)
             (some? c3)
             (nil? c4))))

  (is (let [c1 (lookupPlayer "u1" "p1")
            _ (.updateGist c1 {:email "e"
                               :name "n"})
            e (:email (.gist c1))
            n (:name (.gist c1))
            nn (.nickname c1)
            id (.id c1)
            cs (.countSessions c1)
            _ (.logout c1)
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
                                 :principal  "u1"
                                 :credential "p1"}})
            t (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :principal  "u2"
                                 :credential "p2"}})
            r1 (some-> s .room)
            r2 (some-> t .room)
            ok
            (and (some? r1)
                 (some? r2)
                 (identical? r1 r2)
                 (== 1 (countGameRooms gid))
                 (== 0 (countFreeRooms gid))
                 (.isOpen r1))
            _ (clearFreeRooms gid)
            _ (clearGameRooms gid)]
        (and ok
             (== 0 (countGameRooms gid))
             (== 0 (countFreeRooms gid)))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :principal  "u3"
                                 :credential "p3"}})
            ^GameRoom r (some-> s .room)
            ok
            (and (some? r)
                 (== 1 (countFreeRooms gid))
                 (not (.canOpen r)))
            _ (removeFreeRoom gid (.id r))]
        (and ok
             (== 0 (countFreeRooms gid)))))

  (is (let [gid "game-1"
            s (doPlayReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :principal  "u4"
                                 :credential "p4"}})
            ^GameRoom r (some-> s .room)
            na (not (.canOpen r))
            nok (not (.isOpen r))
            t (doJoinReq {:source (mockPluglet)
                          :body {:gameid gid
                                 :roomid (some-> r (.id))
                                 :principal  "u5"
                                 :credential "p5"}})
            r2 (some-> t .room)]
        (and (some? r)
             (some? r2)
             na
             nok
             (identical? r r2)
             (.isOpen r2)
             (== 1 (countGameRooms gid))
             (== 0 (countFreeRooms gid))
             (do->true (clearGameRooms gid))
             (do->true (clearFreeRooms gid)))))

  (is (string? "That's all folks!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

