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

  czlab.loki.util

  (:require [czlab.basal.core :as c])

  (:import [java.lang Math]
           [clojure.lang APersistentVector]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def RMSN :room-and-session)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn deg->rad

  ""
  [deg]

  (* deg (/ Math/PI 180)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn rad->deg

  ""
  [rad]

  (* rad (/ 180 Math/PI)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn dxdy

  "Calculate object's positional deltas after tick"
  ^APersistentVector
  [{:keys [speed theta] :as obj} dt]

  [(* dt speed (Math/cos theta))
   (* dt speed (Math/sin theta))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn moveObject!

  "Move object to new position after tick"

  ([obj dt]
   (moveObject! obj dt true))
  ([{:keys [x y] :as obj} dt openGL?]
   (let [[dx dy] (dxdy obj dt)]
     (if openGL?
       (merge obj {:x (+ x dx)
                   :y (+ y dy)})
       (merge obj {:x (+ x dx)
                   :y (- y dy)})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

