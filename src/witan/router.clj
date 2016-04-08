(ns witan.router
  (:require [clojure.tools.cli :refer [parse-opts]]
            [me.raynes.fs :as fs]
            [me.raynes.conch :as sh]
            [me.raynes.conch.low-level :as shl]
            [clojure.java.io :as io]
            [clostache.parser :as parser])
  (:gen-class))

;; declare docker
(sh/programs docker)

;;
(def docker-name-regex
  #"([a-zA-Z0-9/\.-]+):?([a-zA-Z0-9\.-]+)?")

(def route-clean-regex
  #"^\s*/(.+)/\s*$")

(def cli-options
  [["-m" "--mode MODE" "Desired mode (dev, prod, etc)"
    :default :prod
    :parse-fn keyword]
   ["-c" "--config CONFIG" "Config file"
    :validate [#(and (fs/exists? %) (-> % slurp read-string)) "Must be a valid file"]]
   ["-d" "--docker NAME[:TAG]" "Creates a docker image with the given name"
    :default nil
    :validate [#(re-find docker-name-regex %) "Must be name:tag format"]]
   ["-h" "--help"]])

(defn usage
  ([summary error]
   (println "witan.router - builds an nginx docker image")
   (when error (println "ERROR:" error))
   (println "Usage:")
   (println summary))
  ([summary]
   (usage summary nil)))

(defn ->server-entry
  [mode [route modes]]
  (let [cleaned-route (second (re-find route-clean-regex route))
        route-safe (clojure.string/replace cleaned-route "/" "-")
        args (-> modes
                 (get mode)
                 (assoc :route cleaned-route
                        :route-safe route-safe))]
    (parser/render "location ~* /{{route}}/(.+) {
            access_log /var/log/nginx/access-{{route-safe}}.log;
            real_ip_header X-Forwarded-For;
            set_real_ip_from 0.0.0.0/0;
            rewrite ^/{{route}}/(.+)$ /$1 break;
            proxy_pass http://{{destination}}:{{port}};
        }" args)))

(defn create-nginx-conf
  [mode config]
  (let [servers (->> config
                     (slurp)
                     (read-string)
                     (map (partial ->server-entry mode))
                     (clojure.string/join "\n\t"))]
    (parser/render-resource "nginx.config.template" {:servers servers})))

(defn create-docker-image
  [name config]
  (let [tmp-dir         (fs/temp-dir "witan-nginx")
        dockerfile-path (str tmp-dir "/" "Dockerfile")
        config-path     (str tmp-dir "/" "nginx.conf")
        dockerfile      (slurp (io/resource "Dockerfile"))]
    (spit config-path     config)
    (spit dockerfile-path (slurp (io/resource "Dockerfile")))
    (println "Bulding docker image:" name)
    (let [result (docker "build" "-t" name tmp-dir {:verbose true})]
      (println (:stdout result))
      (println (:stderr result)))))

(defn do-stuff
  [{:keys [mode config docker]}]
  (let [nginx-conf (create-nginx-conf mode config)]
    (if docker
      (do
        (create-docker-image docker nginx-conf)
        (println "To run use: docker run -p 80:80" docker )
        (System/exit 0))
      (println nginx-conf))))

(defn -main [& args]
  (let [opts (parse-opts args cli-options)]
    (cond
      (-> opts :options :help)         (usage (:summary opts))
      (:errors opts)                   (usage (:summary opts) (clojure.string/join "\n" (:errors opts)))
      :else (do-stuff (:options opts)))))
