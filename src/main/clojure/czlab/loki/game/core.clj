;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.core

  (:require [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.basal.core]
        [czlab.basal.str])

  (:import [czlab.loki.game Game]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *game-rego* {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn initGameRegistry!
  "Initialize the game registry"
  [games]
  {:pre [(map? games)]}
  (alter-var-root #'czlab.loki.game.core/*game-rego*
                  (constantly games)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGame
  "Find a game from the registry"
  ^Game
  [gameid]
  (when-some [g (*game-rego* (keyword gameid))]
    (let [{:keys [enabled? minp maxp engine]
           :or {enabled? false
                minp 1
                maxp 1
                engine ""}}
          (:network g)]
      (log/debug "found game with uuid = %s" gameid)
      (reify Game
        (supportMultiPlayers [_] (boolean enabled?))
        (maxPlayers [_] (if (spos? maxp)
                          (int maxp)
                          (int 9)))
        (minPlayers [_] (if (spos? minp)
                          (int minp)
                          (int 1)))
        (name [_] (:name g))
        (engineClass [_] engine)
        (gist [_] g)
        (id [_] gameid)
        (unload [_] )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


