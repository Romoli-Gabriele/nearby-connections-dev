//
//  GoogleNearbyMessagesBridge.m
//  native1
//
//  Created by Gabriele Romoli on 02/05/23.
//

#import <Foundation/Foundation.h>
#import "native1-Bridging-Header.h"
#import "React/RCTBridgeModule.h"
#import "React/RCTEventEmitter.h"
//#import <NearbyCoreAdapter.h>
//#import "connections/swift/NearbyCoreAdapter/Sources/Public/NearbyCoreAdapter/GNCAdvertisingOptions.h"
//#import "connections/swift/NearbyCoreAdapter/Sources/Public/NearbyCoreAdapter/GNCSupportedMediums.h"
//#import "connections/swift/NearbyCoreAdapter/Sources/Public/NearbyCoreAdapter/GNCStrategy.h"

@interface RCT_EXTERN_REMAP_MODULE(GoogleNearbyMessages, NearbyMessages, NSObject)
RCT_EXTERN_METHOD(start:(NSString)message);
RCT_EXTERN_METHOD(stop);
/*RCT_EXTERN_METHOD(connect:(NSString)apiKey discoveryModes:(NSArray)discoveryModes discoveryMediums:(NSArray)discoveryMediums resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
//RCT_EXTERN_METHOD(disconnect);
RCT_EXTERN_METHOD(publish:(NSString)message resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(unpublish:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(subscribe:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(unsubscribe);*/
RCT_EXTERN_METHOD(checkBluetoothPermission:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(checkBluetoothAvailability:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
@end



/*@implementation GNCAdvertisingOptions

- (instancetype)initWithStrategy:(GNCStrategy)strategy {
  self = [super init];
  if (self) {
    _strategy = strategy;
    _autoUpgradeBandwidth = YES;
    _mediums = [[GNCSupportedMediums alloc] initWithAllMediumsEnabled];
  }
  return self;
}

@end
*/
