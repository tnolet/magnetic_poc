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

        var registerCallback = function(callback){
            source.addEventListener('message',function(msg){
              callback(JSON.parse(msg.data));
            });
        };

        return {
            register: function(callback) {
                registerCallback(callback);
            }
        };

})

.service('Streamliner', ['$rootScope', '$http', function ($rootScope, $http) {
    /* private */
    var jobs = [];

    var requestJobStatus = function (jobId) {
        console.info('>> requestJobStatus');
        console.info(arguments);

        var currentJob = jobs[jobId];

        $http.get('/jobs/' + jobId)
            .success(function(data, status, headers, config) {
                if (!currentJob || currentJob.status !== data.status) {
                  currentJob.status = data.status;
                  $rootScope.$broadcast('jobs.update', data);
                }

                requestJobEvents(jobId);
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

    var startPolling = function (jobId) {
        var job = jobs[jobId] || {
          events: []
        };

        job.timerId = window.setInterval(requestJobStatus.bind(jobs, jobId), 1500);
        jobs[jobId] = job;
    };

    var stopPolling = function (jobId) {
        if (jobs[jobId] && jobs[jobId].timerId) {
          window.clearInterval(jobs[jobId].timerId);
        }
    };

    return {
      addJob: startPolling
    };
}]);
