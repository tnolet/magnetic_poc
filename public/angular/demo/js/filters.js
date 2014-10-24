'use strict';

/* Filters */
// need load the moment.js to use this filter. 
angular.module('app.filters', [])

    // Time Filters

  .filter('fromNow', function() {
    return function(date) {
      return moment(date).fromNow();
    }

  })
.filter('secondsSince',function(){
        return function(seconds){
            return moment().startOf('day')
                .seconds(seconds)
                .format('H:mm:ss');
        }
    })

    // Model and status filter

    .filter('deletedContainers',function(){
        return function(containers){
            return containers.filter(function(cnt){ return cnt.status != 'DESTROYED' })
        }
    })

.filter('destroyedSlas',function(){
    return function(slas){
        return slas.filter(function(cnt){ return cnt.state != 'DESTROYED' })
    }
});
