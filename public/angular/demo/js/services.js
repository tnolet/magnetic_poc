/* global window, angular, console, EventSource, $ */
'use strict';

/* Services */

angular.module('app.services', [])

    /* Feeds */

/**
 * Simple service that receives a SSE stream of load balancer metrics
 * @return register return a function to which you can register a callback.
 * Typically, you would register a callback from a controller to receive live updates
 * from the metrics feed in a controller $scope
 *
 */

.factory('loadBalancerMetricsFeed', function () {
    console.log("Registered service loadBalancerMetricsFeed");

    var source = new EventSource('/feeds/metrics/lb');

    var registerCallback = function (callback) {
        source.addEventListener('message', function (msg) {
          callback(JSON.parse(msg.data));
        });
    };

    return {
        register: function(callback) {
            registerCallback(callback);
        }
    };

})
.service('Polling', ['$rootScope', '$http', function ($rootScope, $http) {
  /**
  * Roughly based on https://www.altamiracorp.com/blog/employee-posts/simple-polling-service-in-angularjs
  */
  var pollers = {};
  var DEFAULT_INTERVAL = 1500;
  var that = this;

  this.startPolling = function (name, url, $scope, success) {
    if (pollers[name]) {
      return;
    }
    var poller = function () {
      $http.get(url).then(function (data) {
        success(data.data);
      }, function () {
        that.destroyPoller(name);
      });
    };
    poller();
    pollers[name] = window.setInterval(poller, DEFAULT_INTERVAL);

    $scope.$on('$destroy', function () {
      that.destroyAllPollers();
    });
  };

  this.destroyPoller = function (name) {
    if (!pollers[name]) {
      return true;
    }
    window.clearInterval(pollers[name]);
    delete pollers[name];
  };

  this.destroyAllPollers = function () {
    angular.forEach(pollers, function (timer, name) {
      that.destroyPoller(name);
    });
  };
}])
.factory('Streamliner', ['$rootScope', '$http', function ($rootScope, $http) {
    /* private */
    var jobs = {};
    var timers = {};
    var INTERVAL = 1250;

    var isNewJob = function (job) {
      return !jobs || typeof jobs[job.id] === 'undefined' || !angular.equals(jobs[job.id], job);
    };

    var handleJob = function (callback, job) {
      if (!job.id || !isNewJob(job)) {
        return;
      }

      if (typeof callback === 'function') {
        callback(job);
      }

      jobs[job.id] = job;
    };

    var requestJobs = function (callback) {
      console.log('>> requestJobs');

      $http.get('/jobs?filter=10')
        .success(function (data, status, headers, config) {
          if (!angular.equals(data, jobs)) {
            callback(data);
            jobs = data;
          }
        })
        .error(function(data, status, headers, config) {
          stopPolling('*');
        });
    };

    var requestJobStatus = function (jobId) {
        console.info('>> requestJobStatus');
        console.info(arguments);

        var currentJob = jobs[jobId];

        $http.get('/jobs/' + jobId)
            .success(function(data, status, headers, config) {
              handleJob(data, function (job) {
                $rootScope.$broadcast('jobs.update', job);
                requestJobEvents(job.id);
              });
            })
            .error(function(data, status, headers, config) {
                console.error(data);
                stopPolling(jobId);
            });
    };

    var requestJobEvents = function (jobId) {
        console.info('>> requestJobEvents');
        console.info(arguments);

        var currentJob = jobs[jobId];

        $http.get('/jobs/' + jobId + '/events')
            .success(function(data, status, headers, config) {
                var toUpdate = [];

                angular.forEach(data, function (event) {
                  if (currentJob.events && currentJob.events[event.id] && currentJob.events[event.id] === event.status) {
                    return;
                  }

                  currentJob.events[event.id] = event.status;
                  toUpdate.push(event);
                });

                $rootScope.$broadcast('jobevent.update', toUpdate);
            })
            .error(function(data, status, headers, config) {
                console.error(data);
                stopPolling(jobId);
            })
        ;
    };

    var stopPolling = function (timerId) {
        if (timers[timerId]) {
          window.clearInterval(timers[timerId]);
        }
    };

    return {
      singleJob: function (jobId) {
          var job = jobs[jobId] || {
            events: []
          };

          timers[jobId] = window.setInterval(requestJobStatus.bind(jobs, jobId), INTERVAL);
          jobs[jobId] = job;
      },
      allJobs: function (callback) {
        requestJobs(callback);
        
        timers['*'] = window.setInterval(requestJobs.bind(jobs, callback), INTERVAL);
      }
    };
}]);
