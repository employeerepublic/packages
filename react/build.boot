(set-env!
  :resource-paths #{"resources"}
  :dependencies '[[adzerk/bootlaces "0.1.13"]
                  [cljsjs/boot-cljsjs "0.5.0" :scope "test"]])

(require
 '[adzerk.bootlaces :refer :all]
 '[cljsjs.boot-cljsjs.packaging :refer :all])

(def +lib-version+ "0.14.7")
(def +version+ (str +lib-version+ "-0"))

(def checksums
  {'cljsjs/react
   {:dev "9827F329E05158D465B92FCA097B62BA",
    :min "9BA549A9A66AB9C7E8EAE26D39FCDC5F"},
   'cljsjs/react-with-addons
   {:dev "94EC7E714FA1B9A6C5E12D3328C1B3F1",
    :min "DB96F1157B73649E2B3AB292C8A00BC0"},
   'cljsjs/react-dom
   {:dev "4727B1A3E7487B3DE93E0982D2111F12",
    :min "120C66F0F9C6E5B7813D62FA445F8996"},
   'cljsjs/react-dom-server
   {:dev "7DAB83C92CDB8A3D724E85E160783AFB",
    :min "F8FA5776A7014CDE8EF038404268AF0A"}})

(task-options!
 pom  {:project     'cljsjs/react
       :version     +version+
       :description "A Javascript library for building user interfaces"
       :url         "http://facebook.github.io/react/"
       :scm         {:url "https://github.com/cljsjs/packages"}
       :license     {"BSD" "http://opensource.org/licenses/BSD-3-Clause"}})

;; TODO: Should eventually be included in boot.core
(defn with-files
  "Runs middleware with filtered fileset and merges the result back into complete fileset."
  [p middleware]
  (fn [next-handler]
    (fn [fileset]
      (let [merge-fileset-handler (fn [fileset']
                                    (next-handler (commit! (assoc fileset :tree (merge (:tree fileset) (:tree fileset'))))))
            handler (middleware merge-fileset-handler)
            fileset (assoc fileset :tree (reduce-kv
                                          (fn [tree path x]
                                            (if (p x)
                                              (assoc tree path x)
                                              tree))
                                          (empty (:tree fileset))
                                          (:tree fileset)))]
        (handler fileset)))))

(defn package-part [{:keys [extern-name namespace project dependencies requires dev-url min-url]}]
  (with-files (fn [x] (= extern-name (.getName (tmp-file x))))
    (comp
     (download :url (or dev-url (format "http://fb.me/%s-%s.js" (name project) +lib-version+))
               :checksum (:dev (get checksums project)))
     (download :url (or min-url (format "http://fb.me/%s-%s.min.js" (name project) +lib-version+))
               :checksum (:min (get checksums project)))
     (sift :move {(re-pattern (format "^%s-%s.js$" (name project) +lib-version+))     (format "cljsjs/%1$s/development/%1$s.inc.js" (name project))
                   (re-pattern (format "^%s-%s.min.js$" (name project) +lib-version+)) (format "cljsjs/%1$s/production/%1$s.min.inc.js" (name project))})
      (sift :include #{#"^cljsjs"})
      (deps-cljs :name namespace :requires requires)
      (pom :project project :dependencies (or dependencies []))
      (jar))))

(deftask package-react []
  (package-part
    {:extern-name "react.ext.js"
     :namespace "cljsjs.react"
     :project 'cljsjs/react}))

(deftask package-dom []
  (package-part
    {:extern-name "react-dom.ext.js"
     :namespace "cljsjs.react.dom"
     :requires ["cljsjs.react"]
     :project 'cljsjs/react-dom
     :dependencies [['cljsjs/react +version+]]}))

(deftask package-dom-server []
  (package-part
    {:extern-name "react-dom-server.ext.js"
     :namespace "cljsjs.react.dom.server"
     :requires ["cljsjs.react"]
     :project 'cljsjs/react-dom-server
     :dependencies [['cljsjs/react +version+]]}))

(deftask package-with-addons []
  (package-part
    {:extern-name "react.ext.js"
     :namespace "cljsjs.react"
     :project 'mccraigmccraig/react-with-addons
     :dev-url "https://raw.githubusercontent.com/employeerepublic/react-with-addons-and-tap-event-plugin/master/react-with-addons-0.14.7.js"
     :min-url "https://raw.githubusercontent.com/employeerepublic/react-with-addons-and-tap-event-plugin/master/react-with-addons-0.14.7.min.js"}))

(deftask package []
  (comp
    (package-react)
    (package-dom)
    (package-dom-server)
    (package-with-addons)))


(defn md5sum [fileset name]
  (with-open [is  (clojure.java.io/input-stream (tmp-file (tmp-get fileset name)))
              dis (java.security.DigestInputStream. is (java.security.MessageDigest/getInstance "MD5"))]
    (#'cljsjs.boot-cljsjs.packaging/realize-input-stream! dis)
    (#'cljsjs.boot-cljsjs.packaging/message-digest->str (.getMessageDigest dis))))

(deftask load-checksums
  "Task to create checksums map for new version"
  []
  (comp
    (reduce
      (fn [handler project]
        (comp handler
              (download :url (format "http://fb.me/%s-%s.js" (name project) +lib-version+))
              (download :url (format "http://fb.me/%s-%s.min.js" (name project) +lib-version+))))
      identity
      (keys checksums))
    (fn [handler]
      (fn [fileset]
        (clojure.pprint/pprint (into {} (map (juxt identity (fn [project]
                                                              {:dev (md5sum fileset (format "%s-%s.js" (name project) +lib-version+))
                                                               :min (md5sum fileset (format "%s-%s.min.js" (name project) +lib-version+))}))
                                             (keys checksums))))
        fileset))))
