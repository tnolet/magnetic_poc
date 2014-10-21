/* global window, angular, console, EventSource, $, moment, navigator */
'use strict';

/* Controllers */

angular.module('app.controllers', ['pascalprecht.translate', 'ngCookies'])
  .controller('AppCtrl', ['$scope', '$translate', '$localStorage', '$window',
    function ($scope, $translate, $localStorage, $window) {
      // add 'ie' classes to html
      var isIE = !!navigator.userAgent.match(/MSIE/i);
      if (isIE) {
        angular.element($window.document.body).addClass('ie');
      }
      if (isSmartDevice( $window )) {
        angular.element($window.document.body).addClass('smart');
      }

      // config
      $scope.app = {
        name: 'Angulr',
        version: '1.3.0',
        // for chart colors
        color: {
          primary: '#7266ba',
          info:    '#23b7e5',
          success: '#27c24c',
          warning: '#fad733',
          danger:  '#f05050',
          light:   '#e8eff0',
          dark:    '#3a3f51',
          black:   '#1c2b36'
        },
        settings: {
          themeID: 1,
          navbarHeaderColor: 'bg-black',
          navbarCollapseColor: 'bg-white-only',
          asideColor: 'bg-black',
          headerFixed: true,
          asideFixed: false,
          asideFolded: false,
          asideDock: false,
          container: false
        }
      };

      // save settings to local storage
      if (angular.isDefined($localStorage.settings)) {
        $scope.app.settings = $localStorage.settings;
      } else {
        $localStorage.settings = $scope.app.settings;
      }
      $scope.$watch('app.settings', function(){
        if( $scope.app.settings.asideDock  &&  $scope.app.settings.asideFixed ){
          // aside dock and fixed must set the header fixed.
          $scope.app.settings.headerFixed = true;
        }
        // save to local storage
        $localStorage.settings = $scope.app.settings;
      }, true);

      // angular translate
      $scope.lang = { isopen: false };
      $scope.langs = {en:'English', de_DE:'German', it_IT:'Italian'};
      $scope.selectLang = $scope.langs[$translate.proposedLanguage()] || "English";
      $scope.setLang = function(langKey, $event) {
        // set the current lang
        $scope.selectLang = $scope.langs[langKey];
        // You can change the language during runtime
        $translate.use(langKey);
        $scope.lang.isopen = !$scope.lang.isopen;
      };

      function isSmartDevice( $window )
      {
          // Adapted from http://www.detectmobilebrowsers.com
          var ua = $window.navigator.userAgent || $window.navigator.vendor || $window.opera;
          // Checks for iOs, Android, Blackberry, Opera Mini, and Windows mobile devices
          return (/iPhone|iPod|iPad|Silk|Android|BlackBerry|Opera Mini|IEMobile/).test(ua);
      }
  }])


//    START: Magnetic Demo Controllers

    .controller('SystemMetricsCtrl',['$scope','$http', '$interval', function($scope, $http, $interval){

        var getMesosMetrics = function(){

            $http.get('http://localhost:9000/system/mesos/metrics').
                success(function (data) {
                    $scope.cpuUsedPercentage = Math.floor(data['master/cpus_percent'] * 100);
                    $scope.memUsedPercentage = Math.floor(data['master/mem_percent'] * 100);
                    $scope.diskUsedPercentage = Math.floor(data['master/disk_percent'] * 100);

                });

            $scope.cpuTotal = 18;
            $scope.memTotal = 53.4;

        };

        getMesosMetrics();

        $interval(function() { getMesosMetrics() },2000)
    }])

    // Environments

  .controller('EnvironmentsCtrl', ['$scope', '$http', 'Polling', function ($scope, $http, $polling) {
    $polling.startPolling('environments', 'http://localhost:9000/environments', $scope, function (data) {
      $scope.environments = data;
    });
   }])

    .controller('EnvironmentsDetailCtrl', ['$scope', '$stateParams', '$http', 'Polling', function ($scope, $stateParams, $http, $polling) {
        $polling.startPolling('environmentsDetail', 'http://localhost:9000/environments/' + $stateParams.environmentId, $scope, function (data) {
          $scope.environment = data;
        });
    }])


        // Services

    .controller('createServiceModalCtrl', function ($scope, $modalInstance) {

        $scope.ok = function (formData) {

            $modalInstance.close(formData);
        };

        $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
        };
    })

    .controller('StreamlineCtrl', ['$scope', '$http', 'Streamliner', function ($scope, $http, $Streamliner) {
        $Streamliner.allJobs(function (jobs) {
          //callback is only called if anything has changed in the jobs array
          //this means the backend is responsiple for guarding the limits of the
          //messages - it can never give back more then 10 messages.
          $scope.streamline = jobs;
        });

        // OLD-FASHIONED STREAMLINE FILLING - CAN BE INITIATED FROM ANYWHERE
        // $scope.$on('jobs.update', function(event, data) {
        //     if (!data.id) {
        //       return;
        //     }
        //
        //     addToStreamline($scope.streamline, {
        //       type: 'job',
        //       queue: data.queue,
        //       status: data.status
        //     });
        // });

        // $scope.$on('jobevent.update', function(event, data) {
        //     angular.forEach(data, function (value) {
        //       addToStreamline($scope.streamline, {
        //         type: 'event',
        //         timestamp: moment(value.timestamp).format('ddd, D MMM HH:mm:ss'),
        //         data: value.status
        //       });
        //     });
        // });
    }])

    .controller('ServicesCtrl',[ '$scope', '$http', '$modal', 'Polling', function ($scope, $http, $modal, $polling) {

        $polling.startPolling('services', 'http://localhost:9000/services', $scope, function (data) {
          $scope.services = data;
        });


        var createService = function(serviceObject) {
            $http.post('http://localhost:9000/services', serviceObject).
                success(function(data){
                    //  Removed single job callback due to new streamline optimizations
                    //  $Streamliner.singleJob(data.jobId);
                });
        };

        $scope.openCreateModal = function(){
            var modalInstance = $modal.open({
                templateUrl: 'demo/tpl/modals/createServiceModal.html',
                controller: 'createServiceModalCtrl',
                size: ''
            });

            modalInstance.result.then(function(formData) {
                var serviceObject = {};
                serviceObject.environmentId = parseInt(formData.environmentId);
                serviceObject.serviceTypeId = parseInt(formData.serviceTypeId);


                createService(serviceObject);
                console.log('Modal dismissed with: ' + JSON.stringify(formData));
            });

        };
    }])

    .controller('ServicesDetailCtrl',[ '$scope', '$stateParams','$http','$timeout', 'Polling', function ($scope, $stateParams, $http, $timeout, $polling) {


        $scope.metrics = {};

        $scope.deleteService = function(){
                $http.delete('http://localhost:9000/services/' + $stateParams.serviceId)
                    .success(function(data, status, headers, config) {
                        //  Removed single job callback due to new streamline optimizations
                        //  $Streamliner.singleJob(data.jobId);
                    })
                    .error(function(data, status, headers, config) {
                        // @todo: implement?
                    });
            };

            var metricSnapshot = function(vrn){

                var params = {
                    vrn: vrn
                };

                $http.get('/metrics/lb/service', { params: params }).
                    success(function(data) {
                        console.log(data)
                        $scope.metrics.frontend = data[0]
                        $scope.metrics.backend = data[1]
                    })

            };

            // create and update Frontend charts

            var graphDataFe = function(source, wantedMetric, proxy, proxyType, relativeTime, timeUnit){

                var params = {
                    source: source,
                    metric: wantedMetric,
                    proxyType: proxyType,
                    proxy: proxy,
                    relativeTime: relativeTime,
                    timeUnit: timeUnit
                };

                $http.get('/metrics/lb', { params: params }).
                    success(function(data) {
                    var values = data.queries[0].results[0].values;

                    var rows = [];
                    for ( var i = 0; i < values.length; i++ ) {
                        rows[i] = {c: [{ v: new Date(values[i][0])},{ v: values[i][1]}]}
                    }

                        // add the last value to the scope ass the current value
                        $scope.metrics_frontend_scur = values[values.length - 1][1];

                        // add the list of values ot the chart
                        $scope.chartFe.data.rows = rows
                })

            };


        var chartFe = {};
        chartFe.type = "AreaChart";

        chartFe.data = {"cols": [
            {id: "time", label: "time", type: "datetime"},
            {id: "sessions", label: "Sessions", type: "number"}

        ], "rows": [] };

        chartFe.options = {
            backgroundColor: "#F0F3F4",
            chartArea: {
                backgroundColor: "#F0F3F4",
                width: "90%"
                },
            width: "100%",
            height: 200,
            colors: ["#23b7e5"],
            legend: { position: "none" },
            isStacked: true,
            fill: 20,
            displayExactValues: true,
            vAxis: {
                 viewWindow: { min: 0 },
                 baselineColor: '#58666e',
                 textStyle: { color: "#58666e", fontName: "Source Sans Pro"},
                    gridlines : {count: -1, color:'#B9B9B9'},
                minorGridLines : { count:5 }

            },
            hAxis: {
                format: "hh:mm:ss",
                baselineColor: '#58666e',
                textStyle: { color: "#58666e", fontName: "Source Sans Pro"}
            },
            animation: {
                duration: 1000,
                easing: 'in'
            }
        };

        chartFe.formatters = {};

        $scope.chartFe = chartFe;

        // create and update Backend charts

        var graphDataBe = function(source, wantedMetric, proxy, proxyType, relativeTime, timeUnit){

            var params = {
                source: source,
                metric: wantedMetric,
                proxyType: proxyType,
                proxy: proxy,
                relativeTime: relativeTime,
                timeUnit: timeUnit
            };

            $http.get('/metrics/lb', { params: params }).
                success(function(data) {
                    var values = data.queries[0].results[0].values;

                    var rows = [];
                    for ( var i = 0; i < values.length; i++ ) {
                        rows[i] = {c: [{ v: new Date(values[i][0])},{ v: values[i][1]}]}
                    }

                    // add the last value to the scope ass the current value
                    $scope.metrics_frontend_scur = values[values.length - 1][1];

                    // add the list of values ot the chart
                    $scope.chartBe.data.rows = rows
                })

        };


        var chartBe = {};
        chartBe.type = "AreaChart";

        chartBe.data = {"cols": [
            {id: "time", label: "time", type: "datetime"},
            {id: "sessions", label: "Sessions", type: "number"}

        ], "rows": [] };

        chartBe.options = {
            backgroundColor: "#F0F3F4",
            chartArea: {
                backgroundColor: "#F0F3F4",
                width: "90%"
            },
            width: "100%",
            height: 200,
            colors: ["#23b7e5"],
            legend: { position: "none" },
            isStacked: true,
            fill: 20,
            displayExactValues: true,
            vAxis: {
                viewWindow: { min: 0 },
                baselineColor: '#58666e',
                textStyle: { color: "#58666e", fontName: "Source Sans Pro"},
                gridlines : {count: -1, color:'#B9B9B9'},
                minorGridLines : { count: 5 }

            },
            hAxis: {
                format: "hh:mm:ss",
                baselineColor: '#58666e',
                textStyle: { color: "#58666e", fontName: "Source Sans Pro"},
                minorGridLines : { count: 5 }

            },
            animation: {
                duration: 1000,
                easing: 'in'
            }
        };

        chartBe.formatters = {};

        $scope.chartBe = chartBe;



            // initialise the controller with all basic info
            $polling.startPolling('servicesDetail', 'http://localhost:9000/services/' + $stateParams.serviceId, $scope, function (data) {
                $scope.vrn = data.vrn;
                $scope.containers = data.containers;
                $scope.port = data.port;
                $scope.mode = data.mode;
                $scope.serviceType = data.serviceType;
                $scope.environment = data.environment;
                $scope.version = data.version;
                graphDataFe("loadbalancer","scur",data.vrn,"frontend","10","minutes");
                graphDataBe("loadbalancer","scur",data.vrn,"frontend","10","minutes");
                metricSnapshot(data.vrn)
            });
    }])

        // Backends AKA Containers


    .controller('ContainersCtrl',[ '$scope', '$http', 'Polling', function ($scope, $http, $polling) {
      $polling.startPolling('containers', 'http://localhost:9000/containers', $scope, function (data) {
        $scope.containers = data;
      });
    }])

    .controller('ContainerDetailCtrl',[ '$scope', '$stateParams','$http',function ($scope, $stateParams, $http) {

        // local constants and functions
        var WEIGHT_MAX = 256;
        var WEIGHT_MIN = 0;
        var WEIGHT_STEP = 5;

        // this metrics filer is a workaround until we have aggregated metrics on the backend app
        // For now, we need to aggregate all the metrics from individual instances/servers belonging to this container

        // aggregate and calculate the most necessary metrics
        var aggregateMetrics = function(metricData){

            var data = metricData;
            var result = {};

            // list which metrics you want aggregated and if you want to average or total them
            var wantedMetrics=[{ "name" : "scur", "mode" : "tot" },{ "name" : "rtime", "mode" : "avg"}];

            wantedMetrics.forEach(function(m){

                var holder = [];
                var sum = 0;
                data.forEach(function(d){
                    holder.push(d[m.name]);
                });

                for(var i = 0; i < holder.length; i++){
                    sum += parseInt(holder[i], 10);
                }

                if (m.mode == "avg") {
                    var avg = sum/holder.length;
                    result[m.name] = avg;
                } else {
                    result[m.name] = sum;
                }

                $scope.metrics = result;

            });

        };


        var metricsFilter = function(metricData){
            var filterMetricData = metricData.filter(function (obj) {
              if (!$scope.instanceVrns()) {
                return;
              }
              return $scope.instanceVrns().indexOf(obj.svname) > -1;
            });
            aggregateMetrics(filterMetricData);
        };

        var updateWeightOnServer = function(serviceId,containerVrn, weight) {

            $http.post('http://localhost:9000/services/' + serviceId + '/containers/' + containerVrn + '/weight/' + weight).
            success(function(data) {
                console.log('updated weight OK:' + $scope.weight);
                //  Removed single job callback due to new streamline optimizations
                //  $Streamliner.singleJob(data.jobId);
            });
        };

        var updateContainerInstances = function(serviceId,containerVrn,amount){
           $http.post('http://localhost:9000/services/' + serviceId + '/containers/' + containerVrn+ '/amount/' + amount).
               success(function(data) {
                   console.log(data);
                   //  Removed single job callback due to new streamline optimizations
                   //  $Streamliner.singleJob(data.jobId);
               });
        };


        // scoped functions

        // add to the weight of the container
        $scope.addWeight = function(){

            // keep the weight under WEIGHT_MAX
            if ($scope.weight < WEIGHT_MAX) {
                $scope.weight += WEIGHT_STEP;
            }

            if( $scope.weight >= WEIGHT_MAX - WEIGHT_STEP) {
                $scope.weight = WEIGHT_MAX;
            }

            // update the weight
            updateWeightOnServer($stateParams.serviceId, $scope.vrn, $scope.weight);

        };

        // subtract from the weight of the container
        $scope.subtractWeight = function(){

            if( $scope.weight <= WEIGHT_MIN + WEIGHT_STEP) {
                $scope.weight = WEIGHT_MIN;
            }

            else if ($scope.weight > WEIGHT_MIN) {
                $scope.weight -= WEIGHT_STEP;
            }

            updateWeightOnServer($stateParams.serviceId, $scope.vrn, $scope.weight);
        };

        // delete the container
        $scope.deleteContainer = function() {
            $http.delete('http://localhost:9000/containers/' + $scope.id).
                success(function(data) {
                    console.log(data);
                    //  Removed single job callback due to new streamline optimizations
                    //  $Streamliner.singleJob(data.jobId);
                });
        };

        //scale in the container instances
        $scope.scaleIn = function() {

            if ($scope.instances.length >= 1) {
                var amount = $scope.instances.length - 1;
                updateContainerInstances($stateParams.serviceId,$scope.vrn,amount);
            }
        };

        //scale out the container instances
        $scope.scaleOut = function() {
            var amount = $scope.instances.length + 1;
            updateContainerInstances($stateParams.serviceId,$scope.vrn,amount);

        };

        $scope.instanceVrns = function() {
            if (!$scope.instances) {
              return;
            }
            var vrns = [];
            $scope.instances.forEach(function(instance){
                    vrns.push(instance.vrn);
                }
            );
            return vrns;
        };

        // initialise the controller with all basic info
        $scope.init = function(container)
        {
            $scope.id = container.id;
            $scope.vrn = container.vrn;
            $scope.imageRepo = container.imageRepo;
            $scope.imageVersion = container.imageVersion;
            $scope.version = container.imageVersion;
            $scope.weight = container.masterWeight;
            $scope.status = container.status;
            $scope.instances = container.instances;
            $scope.instanceAmount = container.instanceAmount;
            $scope.created_at = container.created_at;

        };
    }])

    // Container Instances

    .controller('InstancesDetailCtrl', ['$scope', '$http' ,function ($scope, $http) {


        var metricSnapshot = function(vrn){

            var params = {
                vrn: vrn
            };

            $http.get('/metrics/lb/server', { params: params }).
                success(function(data) {
                    console.log(data)
                    $scope.metrics = data[0]
                })

        };


        // initialise the controller with all basic info
        $scope.init = function (instance) {

            $scope.vrn = instance.vrn;
            $scope.host = instance.host;
            $scope.ports = instance.ports;
            metricSnapshot(instance.vrn)

        };
    }])

    // Images

    .controller('deployImageModalCtrl', function ($scope, $modalInstance) {

        $scope.ok = function (serviceVrn) {

            $modalInstance.close(serviceVrn);
        };

        $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
        };
    })

    .controller('createImageModalCtrl', function ($scope, $modalInstance) {

        $scope.ok = function (formData) {

            $modalInstance.close(formData);
        };

        $scope.cancel = function () {
            $modalInstance.dismiss('cancel');
        };
    })


    .controller('ImagesCtrl', ['$scope','$http', '$modal', 'Polling', function ($scope, $http, $modal, $polling) {
        $polling.startPolling('images', 'http://localhost:9000/images', $scope, function (data) {
          $scope.images = data;
        });

        var createImage = function(img) {

            var imageObj = img;
            imageObj.mode = "http";
            imageObj.port = 80;

            $http.post('http://localhost:9000/images', imageObj).
                success(function(data){
                    console.log(data);
                });
        };

        // launches the modal for creating a new image
        $scope.openCreateModal = function () {

            var modalInstance = $modal.open({
                templateUrl: 'demo/tpl/modals/createImageModal.html',
                controller: 'createImageModalCtrl'
            });

            modalInstance.result.then(function(formData) {

                var imageObject = {};
                imageObject.name = formData.name;
                imageObject.repo = formData.repo;
                imageObject.version = formData.version;
                imageObject.arguments = formData.args;
                imageObject.id = 0; // some arbitrary id that will be discarded

                createImage(imageObject);
                console.log('Modal dismissed with: ' + JSON.stringify(formData));
            });

        };


    }])

    .controller('ImagesListItemCtrl',[ '$scope', '$stateParams','$http', '$modal', 'Streamliner', function ($scope, $stateParams, $http, $modal, $Streamliner) {
        // deploys an image to a specific service
        // parameter: id     id of the image
        // parameter: vrn   vrn of the service
        var deployImageToService = function(id,vrn) {
            $http.post('http://localhost:9000/images/' + id + '/deploy?service=' + vrn).
                success(function(data){
                    console.log(data);
                    //  Removed single job callback due to new streamline optimizations
                    //  $Streamliner.singleJob(data.jobId);
                });
        };

        // launches the modal for deploying an image
        $scope.openDeployModal = function() {
            var modalInstance = $modal.open({
                templateUrl: 'demo/tpl/modals/deployImageModal.html',
                controller: 'deployImageModalCtrl',
                size: ''
            });

            modalInstance.result.then(function (serviceVrn) {
                deployImageToService($scope.image.id, serviceVrn);
            });
        };

        $scope.init = function(image) {
            $scope.image = image;
        };
    }])

    .controller('ImagesDetailCtrl',[ '$scope', '$stateParams','$http', '$modal', 'Polling', function ($scope, $stateParams, $http, $modal, $polling) {
//       get details for this image
        $polling.startPolling('imagesDetail', 'http://localhost:9000/images/' + $stateParams.imageId, $scope, function (data) {
          $scope.image = data;
        });

        // also grab all containers that are based on this image
        $polling.startPolling('imagesDetailContainers', 'http://localhost:9000/containers?image=' + $stateParams.imageId, $scope, function (data) {
          $scope.containers = data;
        });
    }])





//    END: Magnetic Demo Controllers





  // bootstrap controller
  .controller('AccordionDemoCtrl', ['$scope', function($scope) {
    $scope.oneAtATime = true;

    $scope.groups = [
      {
        title: 'Accordion group header - #1',
        content: 'Dynamic group body - #1'
      },
      {
        title: 'Accordion group header - #2',
        content: 'Dynamic group body - #2'
      }
    ];

    $scope.items = ['Item 1', 'Item 2', 'Item 3'];

    $scope.addItem = function() {
      var newItemNo = $scope.items.length + 1;
      $scope.items.push('Item ' + newItemNo);
    };

    $scope.status = {
      isFirstOpen: true,
      isFirstDisabled: false
    };
  }])
  .controller('AlertDemoCtrl', ['$scope', function($scope) {
    $scope.alerts = [
      { type: 'success', msg: 'Well done! You successfully read this important alert message.' },
      { type: 'info', msg: 'Heads up! This alert needs your attention, but it is not super important.' },
      { type: 'warning', msg: 'Warning! Best check yo self, you are not looking too good...' }
    ];

    $scope.addAlert = function() {
      $scope.alerts.push({type: 'danger', msg: 'Oh snap! Change a few things up and try submitting again.'});
    };

    $scope.closeAlert = function(index) {
      $scope.alerts.splice(index, 1);
    };
  }])
  .controller('ButtonsDemoCtrl', ['$scope', function($scope) {
    $scope.singleModel = 1;

    $scope.radioModel = 'Middle';

    $scope.checkModel = {
      left: false,
      middle: true,
      right: false
    };
  }])
  .controller('CarouselDemoCtrl', ['$scope', function($scope) {
    $scope.myInterval = 5000;
    var slides = $scope.slides = [];
    $scope.addSlide = function() {
      slides.push({
        image: 'img/c' + slides.length + '.jpg',
        text: ['Carousel text #0','Carousel text #1','Carousel text #2','Carousel text #3'][slides.length % 4]
      });
    };
    for (var i=0; i<4; i++) {
      $scope.addSlide();
    }
  }])
  .controller('DropdownDemoCtrl', ['$scope', function($scope) {
    $scope.items = [
      'The first choice!',
      'And another choice for you.',
      'but wait! A third!'
    ];

    $scope.status = {
      isopen: false
    };

    $scope.toggled = function(open) {
      //console.log('Dropdown is now: ', open);
    };

    $scope.toggleDropdown = function($event) {
      $event.preventDefault();
      $event.stopPropagation();
      $scope.status.isopen = !$scope.status.isopen;
    };
  }])
  .controller('ModalInstanceCtrl', ['$scope', '$modalInstance', 'items', function($scope, $modalInstance, items) {
    $scope.items = items;
    $scope.selected = {
      item: $scope.items[0]
    };

    $scope.ok = function () {
      $modalInstance.close($scope.selected.item);
    };

    $scope.cancel = function () {
      $modalInstance.dismiss('cancel');
    };
  }])
  .controller('ModalDemoCtrl', ['$scope', '$modal', '$log', function($scope, $modal, $log) {
    $scope.items = ['item1', 'item2', 'item3'];
    $scope.open = function (size) {
      var modalInstance = $modal.open({
        templateUrl: 'myModalContent.html',
        controller: 'ModalInstanceCtrl',
        size: size,
        resolve: {
          items: function () {
            return $scope.items;
          }
        }
      });

      modalInstance.result.then(function (selectedItem) {
        $scope.selected = selectedItem;
      }, function () {
        $log.info('Modal dismissed at: ' + new Date());
      });
    };
  }])
  .controller('PaginationDemoCtrl', ['$scope', '$log', function($scope, $log) {
    $scope.totalItems = 64;
    $scope.currentPage = 4;

    $scope.setPage = function (pageNo) {
      $scope.currentPage = pageNo;
    };

    $scope.pageChanged = function() {
      $log.info('Page changed to: ' + $scope.currentPage);
    };

    $scope.maxSize = 5;
    $scope.bigTotalItems = 175;
    $scope.bigCurrentPage = 1;
  }])
  .controller('PopoverDemoCtrl', ['$scope', function($scope) {
    $scope.dynamicPopover = 'Hello, World!';
    $scope.dynamicPopoverTitle = 'Title';
  }])
  .controller('ProgressDemoCtrl', ['$scope', function($scope) {
    $scope.max = 200;

    $scope.random = function() {
      var value = Math.floor((Math.random() * 100) + 1);
      var type;

      if (value < 25) {
        type = 'success';
      } else if (value < 50) {
        type = 'info';
      } else if (value < 75) {
        type = 'warning';
      } else {
        type = 'danger';
      }

      $scope.showWarning = (type === 'danger' || type === 'warning');

      $scope.dynamic = value;
      $scope.type = type;
    };
    $scope.random();

    $scope.randomStacked = function() {
      $scope.stacked = [];
      var types = ['success', 'info', 'warning', 'danger'];

      for (var i = 0, n = Math.floor((Math.random() * 4) + 1); i < n; i++) {
          var index = Math.floor((Math.random() * 4));
          $scope.stacked.push({
            value: Math.floor((Math.random() * 30) + 1),
            type: types[index]
          });
      }
    };
    $scope.randomStacked();
  }])
  .controller('TabsDemoCtrl', ['$scope', function($scope) {
    $scope.tabs = [
      { title:'Dynamic Title 1', content:'Dynamic content 1' },
      { title:'Dynamic Title 2', content:'Dynamic content 2', disabled: true }
    ];
  }])
  .controller('RatingDemoCtrl', ['$scope', function($scope) {
    $scope.rate = 7;
    $scope.max = 10;
    $scope.isReadonly = false;

    $scope.hoveringOver = function(value) {
      $scope.overStar = value;
      $scope.percent = 100 * (value / $scope.max);
    };
  }])
  .controller('TooltipDemoCtrl', ['$scope', function($scope) {
    $scope.dynamicTooltip = 'Hello, World!';
    $scope.dynamicTooltipText = 'dynamic';
    $scope.htmlTooltip = 'I\'ve been made <b>bold</b>!';
  }])
  .controller('TypeaheadDemoCtrl', ['$scope', '$http', function($scope, $http) {
    $scope.selected = undefined;
    $scope.states = ['Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut', 'Delaware', 'Florida', 'Georgia', 'Hawaii', 'Idaho', 'Illinois', 'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana', 'Maine', 'Maryland', 'Massachusetts', 'Michigan', 'Minnesota', 'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada', 'New Hampshire', 'New Jersey', 'New Mexico', 'New York', 'North Dakota', 'North Carolina', 'Ohio', 'Oklahoma', 'Oregon', 'Pennsylvania', 'Rhode Island', 'South Carolina', 'South Dakota', 'Tennessee', 'Texas', 'Utah', 'Vermont', 'Virginia', 'Washington', 'West Virginia', 'Wisconsin', 'Wyoming'];
    // Any function returning a promise object can be used to load values asynchronously
    $scope.getLocation = function(val) {
      return $http.get('http://maps.googleapis.com/maps/api/geocode/json', {
        params: {
          address: val,
          sensor: false
        }
      }).then(function(res){
        var addresses = [];
        angular.forEach(res.data.results, function(item){
          addresses.push(item.formatted_address);
        });
        return addresses;
      });
    };
  }])
  .controller('DatepickerDemoCtrl', ['$scope', function($scope) {
    $scope.today = function() {
      $scope.dt = new Date();
    };
    $scope.today();

    $scope.clear = function () {
      $scope.dt = null;
    };

    // Disable weekend selection
    $scope.disabled = function(date, mode) {
      return ( mode === 'day' && ( date.getDay() === 0 || date.getDay() === 6 ) );
    };

    $scope.toggleMin = function() {
      $scope.minDate = $scope.minDate ? null : new Date();
    };
    $scope.toggleMin();

    $scope.open = function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      $scope.opened = true;
    };

    $scope.dateOptions = {
      formatYear: 'yy',
      startingDay: 1,
      class: 'datepicker'
    };

    $scope.initDate = new Date('2016-15-20');
    $scope.formats = ['dd-MMMM-yyyy', 'yyyy/MM/dd', 'dd.MM.yyyy', 'shortDate'];
    $scope.format = $scope.formats[0];
  }])
  .controller('TimepickerDemoCtrl', ['$scope', function($scope) {
    $scope.mytime = new Date();

    $scope.hstep = 1;
    $scope.mstep = 15;

    $scope.options = {
      hstep: [1, 2, 3],
      mstep: [1, 5, 10, 15, 25, 30]
    };

    $scope.ismeridian = true;
    $scope.toggleMode = function() {
      $scope.ismeridian = ! $scope.ismeridian;
    };

    $scope.update = function() {
      var d = new Date();
      d.setHours( 14 );
      d.setMinutes( 0 );
      $scope.mytime = d;
    };

    $scope.changed = function () {
      //console.log('Time changed to: ' + $scope.mytime);
    };

    $scope.clear = function() {
      $scope.mytime = null;
    };
  }])

  // Form controller
  .controller('FormDemoCtrl', ['$scope', function($scope) {
    $scope.notBlackListed = function(value) {
      var blacklist = ['bad@domain.com','verybad@domain.com'];
      return blacklist.indexOf(value) === -1;
    };

    $scope.val = 15;
    var updateModel = function(val){
      $scope.$apply(function(){
        $scope.val = val;
      });
    };
    angular.element("#slider").on('slideStop', function(data){
      updateModel(data.value);
    });

    $scope.select2Number = [
      {text:'First',  value:'One'},
      {text:'Second', value:'Two'},
      {text:'Third',  value:'Three'}
    ];

    $scope.list_of_string = ['tag1', 'tag2'];
    $scope.select2Options = {
        'multiple': true,
        'simple_tags': true,
        'tags': ['tag1', 'tag2', 'tag3', 'tag4']  // Can be empty list.
    };

  }])

  // Flot Chart controller
  .controller('FlotChartDemoCtrl', ['$scope', function($scope) {
    $scope.d = [ [1,6.5],[2,6.5],[3,7],[4,8],[5,7.5],[6,7],[7,6.8],[8,7],[9,7.2],[10,7],[11,6.8],[12,7] ];

    $scope.d0_1 = [ [0,7],[1,6.5],[2,12.5],[3,7],[4,9],[5,6],[6,11],[7,6.5],[8,8],[9,7] ];

    $scope.d0_2 = [ [0,4],[1,4.5],[2,7],[3,4.5],[4,3],[5,3.5],[6,6],[7,3],[8,4],[9,3] ];

    $scope.d1_1 = [ [10, 120], [20, 70], [30, 70], [40, 60] ];

    $scope.d1_2 = [ [10, 50],  [20, 60], [30, 90],  [40, 35] ];

    $scope.d1_3 = [ [10, 80],  [20, 40], [30, 30],  [40, 20] ];

    $scope.d2 = [];

    for (var i = 0; i < 20; ++i) {
      $scope.d2.push([i, Math.sin(i)]);
    }

    $scope.d3 = [
      { label: "iPhone5S", data: 40 },
      { label: "iPad Mini", data: 10 },
      { label: "iPad Mini Retina", data: 20 },
      { label: "iPhone4S", data: 12 },
      { label: "iPad Air", data: 18 }
    ];

    $scope.getRandomData = function() {
      var data = [],
      totalPoints = 150;
      if (data.length > 0)
        data = data.slice(1);
      while (data.length < totalPoints) {
        var prev = data.length > 0 ? data[data.length - 1] : 50,
          y = prev + Math.random() * 10 - 5;
        if (y < 0) {
          y = 0;
        } else if (y > 100) {
          y = 100;
        }
        data.push(y);
      }
      // Zip the generated y values with the x values
      var res = [];
      for (var i = 0; i < data.length; ++i) {
        res.push([i, data[i]]);
      }
      return res;
    };

    $scope.d4 = $scope.getRandomData();
  }])

  // jVectorMap controller
  .controller('JVectorMapDemoCtrl', ['$scope', function($scope) {
    $scope.world_markers = [
      {latLng: [41.90, 12.45], name: 'Vatican City'},
      {latLng: [43.73, 7.41], name: 'Monaco'},
      {latLng: [-0.52, 166.93], name: 'Nauru'},
      {latLng: [-8.51, 179.21], name: 'Tuvalu'},
      {latLng: [43.93, 12.46], name: 'San Marino'},
      {latLng: [47.14, 9.52], name: 'Liechtenstein'},
      {latLng: [7.11, 171.06], name: 'Marshall Islands'},
      {latLng: [17.3, -62.73], name: 'Saint Kitts and Nevis'},
      {latLng: [3.2, 73.22], name: 'Maldives'},
      {latLng: [35.88, 14.5], name: 'Malta'},
      {latLng: [12.05, -61.75], name: 'Grenada'},
      {latLng: [13.16, -61.23], name: 'Saint Vincent and the Grenadines'},
      {latLng: [13.16, -59.55], name: 'Barbados'},
      {latLng: [17.11, -61.85], name: 'Antigua and Barbuda'},
      {latLng: [-4.61, 55.45], name: 'Seychelles'},
      {latLng: [7.35, 134.46], name: 'Palau'},
      {latLng: [42.5, 1.51], name: 'Andorra'},
      {latLng: [14.01, -60.98], name: 'Saint Lucia'},
      {latLng: [6.91, 158.18], name: 'Federated States of Micronesia'},
      {latLng: [1.3, 103.8], name: 'Singapore'},
      {latLng: [1.46, 173.03], name: 'Kiribati'},
      {latLng: [-21.13, -175.2], name: 'Tonga'},
      {latLng: [15.3, -61.38], name: 'Dominica'},
      {latLng: [-20.2, 57.5], name: 'Mauritius'},
      {latLng: [26.02, 50.55], name: 'Bahrain'},
      {latLng: [0.33, 6.73], name: 'São Tomé and Príncipe'}
    ];

    $scope.usa_markers = [
      {latLng: [40.71, -74.00], name: 'New York'},
      {latLng: [34.05, -118.24], name: 'Los Angeles'},
      {latLng: [41.87, -87.62], name: 'Chicago'},
      {latLng: [29.76, -95.36], name: 'Houston'},
      {latLng: [39.95, -75.16], name: 'Philadelphia'},
      {latLng: [38.90, -77.03], name: 'Washington'},
      {latLng: [37.36, -122.03], name: 'Silicon Valley'}
    ];
  }])

  // signin controller
  .controller('SigninFormController', ['$scope', '$http', '$state', function($scope, $http, $state) {
    $scope.user = {};
    $scope.authError = null;
    $scope.login = function() {
      $scope.authError = null;
      // Try to login
      $http.post('api/login', {email: $scope.user.email, password: $scope.user.password})
      .then(function(response) {
        if ( !response.data.user ) {
          $scope.authError = 'Email or Password not right';
        }else{
          $state.go('app.dashboard');
        }
      }, function(x) {
        $scope.authError = 'Server Error';
      });
    };
  }])

  // signup controller
  .controller('SignupFormController', ['$scope', '$http', '$state', function($scope, $http, $state) {
    $scope.user = {};
    $scope.authError = null;
    $scope.signup = function() {
      $scope.authError = null;
      // Try to create
      $http.post('api/signup', {name: $scope.user.name, email: $scope.user.email, password: $scope.user.password})
      .then(function(response) {
        if ( !response.data.user ) {
          $scope.authError = response;
        }else{
          $state.go('app.dashboard');
        }
      }, function(x) {
        $scope.authError = 'Server Error';
      });
    };
  }]);
