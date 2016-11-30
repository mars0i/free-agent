(ns free-agent.matrix
  (:require [clojure.core.matrix :as m]))

(defn col-mat
  "Turns a sequence of numbers xs into a column vector."
  [xs]
  (mx/matrix (map vector xs)))

(defn row-mat
  "Turns a sequence of numbers xs into a row vector."
  [xs]
  (mx/matrix (vector xs)))
