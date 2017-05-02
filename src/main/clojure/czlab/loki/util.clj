;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.util

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.core]
        [czlab.basal.str])

  (:import [clojure.lang APersistentVector]
           [io.netty.util AttributeKey]
           [java.lang Math]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defonce ^AttributeKey RMSN (AttributeKey/newInstance "room+session"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn deg->rad "" [deg] (* deg (/ Math/PI 180)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn rad->deg "" [rad] (* rad (/ 180 Math/PI)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn dxdy
  "Calculate object's positional deltas after tick"
  ^APersistentVector
  [{:keys [speed theta] :as obj} dt]

  [(* dt speed (Math/cos theta))
   (* dt speed (Math/sin theta))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn moveObject!
  "Move object to new position after tick"
  ([obj dt] (moveObject! obj dt true))
  ([{:keys [x y] :as obj} dt openGL?]
   (let [[dx dy] (dxdy obj dt)]
     (if openGL?
       (merge obj {:x (+ x dx)
                   :y (+ y dy)})
       (merge obj {:x (+ x dx)
                   :y (- y dy)})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

