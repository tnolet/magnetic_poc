/* global angular, $, window, screenfull, console */
'use strict';

    /* directives */

angular.module('app.directives', ['ui.load'])
  .directive('uiModule', ['MODULE_CONFIG','uiLoad', '$compile', function(MODULE_CONFIG, uiLoad, $compile) {
    return {
      restrict: 'A',
      compile: function (el, attrs) {
        var contents = el.contents().clone();
        return function(scope, el, attrs){
          el.contents().remove();
          uiLoad.load(MODULE_CONFIG[attrs.uiModule])
          .then(function(){
            $compile(contents)(scope, function(clonedElement, scope) {
              el.append(clonedElement);
            });
          });
        };
      }
    };
  }])
  .directive('uiShift', ['$timeout', function($timeout) {
    return {
      restrict: 'A',
      link: function(scope, el, attr) {
        // get the $prev or $parent of this el
        var _el = $(el),
            _window = $(window),
            prev = _el.prev(),
            parent,
            width = _window.width()
            ;

        if (!prev.length) {
          parent = _el.parent();
        }

        function sm(){
          $timeout(function () {
            var method = attr.uiShift;
            var target = attr.target;
            if (!_el.hasClass('in')) {
              _el[method](target).addClass('in');
            }
          });
        }

        function md(){
          if (parent) {
            parent.prepend(el);
          } else {
            _el.insertAfter(prev);
          }
          _el.removeClass('in');
        }

        if (!(width < 768 && sm())) {
          md();
        }

        _window.resize(function() {
          if(width !== _window.width()){
            $timeout(function(){
              if (!(_window.width() < 768 && sm())) {
                md();
              }
              width = _window.width();
            });
          }
        });
      }
    };
  }])
  .directive('uiToggleClass', ['$timeout', '$document', function($timeout, $document) {
    return {
      restrict: 'AC',
      link: function(scope, el, attr) {
        el.on('click', function(e) {
          e.preventDefault();
          var classes = attr.uiToggleClass.split(','),
              targets = (attr.target && attr.target.split(',')) || new Array(el),
              key = 0;
          angular.forEach(classes, function( _class ) {
            var target = targets[(targets.length && key)];
            if (( _class.indexOf( '*' ) !== -1 )) {
              magic(_class, target);
            }
            $( target ).toggleClass(_class);
            key ++;
          });
          $(el).toggleClass('active');

          function magic(_class, target){
            var patt = new RegExp( '\\s' +
                _class.
                  replace( /\*/g, '[A-Za-z0-9-_]+' ).
                  split( ' ' ).
                  join( '\\s|\\s' ) +
                '\\s', 'g' );
            var cn = ' ' + $(target)[0].className + ' ';
            while ( patt.test( cn ) ) {
              cn = cn.replace( patt, ' ' );
            }
            $(target)[0].className = $.trim( cn );
          }
        });
      }
    };
  }])
  .directive('uiNav', ['$timeout', function($timeout) {
    return {
      restrict: 'AC',
      link: function(scope, el, attr) {
        var _window = $(window),
        _mb = 768,
        wrap = $('.app-aside'),
        next,
        backdrop = '.dropdown-backdrop';
        // unfolded
        el.on('click', 'a', function(e) {
          if (next) {
            next.trigger('mouseleave.nav');
          }
          var _this = $(this);
          _this.parent().siblings( ".active" ).toggleClass('active');
          if (_this.next().is('ul') && _this.parent().toggleClass('active')) {
            e.preventDefault();
          }
          // mobile
          if (!_this.next().is('ul')) {
            if (( _window.width() < _mb )) {
              $('.app-aside').removeClass('show off-screen');
            }
          }
        });

        // folded & fixed
        el.on('mouseenter', 'a', function(e){
          if (next) {
            next.trigger('mouseleave.nav');
          }
          $('> .nav', wrap).remove();
          if ( !$('.app-aside-fixed.app-aside-folded').length || ( _window.width() < _mb ) || $('.app-aside-dock').length) return;
          var _this = $(e.target),
              top,
              w_h = $(window).height(),
              offset = 50,
              min = 150;

          if (!_this.is('a')) {
            _this = _this.closest('a');
          }
          if( _this.next().is('ul') ){
             next = _this.next();
          }else{
            return;
          }

          _this.parent().addClass('active');
          top = _this.parent().position().top + offset;
          next.css('top', top);
          if( top + next.height() > w_h ){
            next.css('bottom', 0);
          }
          if(top + min > w_h){
            next.css('bottom', w_h - top - offset).css('top', 'auto');
          }
          next.appendTo(wrap);

          next.on('mouseleave.nav', function(e){
            $(backdrop).remove();
            next.appendTo(_this.parent());
            next.off('mouseleave.nav').css('top', 'auto').css('bottom', 'auto');
            _this.parent().removeClass('active');
          });

          if ($('.smart').length) {
            $('<div class="dropdown-backdrop"/>').insertAfter('.app-aside').on('click', function(next) {
              if (next) {
                next.trigger('mouseleave.nav');
              }
            });
          }

        });

        wrap.on('mouseleave', function(e){
          if (next) {
            next.trigger('mouseleave.nav');
          }
          $('> .nav', wrap).remove();
        });
      }
    };
  }])
  .directive('uiScroll', ['$location', '$anchorScroll', function($location, $anchorScroll) {
    return {
      restrict: 'AC',
      link: function(scope, el, attr) {
        el.on('click', function(e) {
          $location.hash(attr.uiScroll);
          $anchorScroll();
        });
      }
    };
  }])
  .directive('uiFullscreen', ['uiLoad', function(uiLoad) {
    return {
      restrict: 'AC',
      template:'<i class="fa fa-expand fa-fw text"></i><i class="fa fa-compress fa-fw text-active"></i>',
      link: function(scope, el, attr) {
        el.addClass('hide');
        uiLoad.load('js/libs/screenfull.min.js').then(function(){
          if (screenfull && screenfull.enabled) {
            el.removeClass('hide');
          }
          el.on('click', function(){
            var target;
            if (attr.target) {
              target = $(attr.target)[0];
            }
            el.toggleClass('active');
            screenfull.toggle(target);
          });
        });
      }
    };
  }])
  .directive('uiButterbar', ['$rootScope', '$anchorScroll', function($rootScope, $anchorScroll) {
     return {
      restrict: 'AC',
      template:'<span class="bar"></span>',
      link: function(scope, el, attrs) {
        el.addClass('butterbar hide');
        scope.$on('$stateChangeStart', function(event) {
          $anchorScroll();
          el.removeClass('hide').addClass('active');
        });
        scope.$on('$stateChangeSuccess', function( event, toState, toParams, fromState ) {
          event.targetScope.$watch('$viewContentLoaded', function(){
            el.addClass('hide').removeClass('active');
          });
        });
      }
     };
  }])
  .directive('setNgAnimate', ['$animate', function ($animate) {
    return {
        link: function ($scope, $element, $attrs) {
            $scope.$watch( function() {
                return $scope.$eval($attrs.setNgAnimate, $scope);
            }, function(valnew, valold){
                $animate.enabled(!!valnew, $element);
            });
        }
    };
  }])
  .directive('uiFocus', function($timeout, $parse) {
    return {
      link: function(scope, element, attrs) {
        var model = $parse(attrs.uiFocus);
        scope.$watch(model, function(value) {
          if(value === true) {
            $timeout(function() {
              element[0].focus();
            });
          }
        });
        element.bind('blur', function() {
           scope.$apply(model.assign(scope, false));
        });
      }
    };
  })
  /* Modals */
  .directive("openDeployModal", ['$modal', '$http', 'Polling', function ($modal, $http, $polling) {
      return {
        restrict: "A",
        link: function (scope, el, attrs) {
          el.on('click', function (e) {
            e.preventDefault();

            //Hack, but necessary to prevent wrong scope object reference
            //$polling.destroyAllPollers();

            var deployModalData = JSON.parse(attrs.openDeployModal);
            var modalInstance;

            if (!deployModalData.service) {
              $http.get('/services').success(function (data) {
                scope.modalServices = data;
              });
            }

            if (!deployModalData.image) {
              $http.get('/images').success(function (data) {
                scope.modalImages = data;
              });
            }

            scope.modalData = deployModalData;
            scope.formData = angular.copy(scope.modalData);

            //Hide or show the modal
            modalInstance = $modal.open({
                templateUrl: 'demo/tpl/modals/deployImageModal.html',
                controller: 'deployImageModalCtrl',
                size: '',
                scope: scope
            }).result.then(function (formData) {
              $http.post('/images/' + formData.image + '/deploy?service=' + formData.service + '&ha=' + formData.ha).
                success(function (data) {
                  console.log(data);
                });
            });
          });
        }
      };
    }])
    /* graphs */

    .directive('sparklinesChart', function () {
        return {
            scope: {
                metrics: '=',
                start: '='
            },
            template: '<div class="spark-lines"></div>',
            restrict: 'E',
            link: function(scope, element, attrs) {

                var myvalues = [0];
                var prevVal = 0;

                scope.$watch('metrics', function(newVal) {

                    if (!newVal) {
                        newVal = prevVal;
                    }
                    prevVal = newVal;

                    if (myvalues.length == attrs.history) {
                        myvalues.shift();

                    }
                    myvalues.push(parseInt(newVal));


                    var graph = element.find('.spark-lines');

                    if (attrs.type == 'bar') {
                        // calculate the bar width based on history  and containing element's width
                        var barSpacing = 1;
                        var barWidth = (graph.width() - ( attrs.history * barSpacing)) / attrs.history ;

                        if (graph.sparkline) {
                            graph.sparkline(myvalues,{type: 'bar', height: attrs.height, barWidth: barWidth, barSpacing: barSpacing, barColor:'#dce5ec'});
                        }
                    } else if ( attrs.type == 'line') {
                        if (graph.sparkline) {
                            graph.sparkline(myvalues, {type: 'line', height: attrs.height, width: '100%', lineWidth:2, valueSpots:{'0:':'#fff'}, lineColor:'#fff', spotColor:'#fff', fillColor:'#dce5ec', highlightLineColor:'#fff', spotRadius:3});
                        }
                    }
                });
            }
        };
    });
