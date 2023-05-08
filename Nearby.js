import {
  NativeModules,
  Platform,
  PermissionsAndroid,
  NativeEventEmitter,
} from 'react-native';

const isAndroid = Platform.OS == 'android'
const isIos = Platform.OS == "ios";


/**
 * Inizia la pubblicazione (anche in background per Android) e lo scanning dei messaggi
 * @param {string} message
 */
const start = message => {
  if (isAndroid) {
    NativeModules.MyNativeModule.start();
    NativeModules.MyNativeModule.startActivity(message);
  } else if (isIos) {
    NativeModules.GoogleNearbyMessages.checkBluetoothPermission().then(
      granted => {
        NativeModules.GoogleNearbyMessages.checkBluetoothAvailability().then(
          available => {
            NativeModules.GoogleNearbyMessages.start(message);
          }
        )
      }
    )


  }
};

/**
 * Interrompe l'attività di pubblicazione e scanning
 */
const stop = () => {
  if (isAndroid) NativeModules.MyNativeModule.stop();
  else if (isIos) {
    NativeModules.GoogleNearbyMessages.stop();
    console.log("Disconnetti");
  }
};

const registerToEvents = (
  onMessageFound,
  onMessageLost,
  onActivityStart,
  onActivityStop,
) => {
  const emitters = [];

  let eventEmitter;
  eventEmitter = new NativeEventEmitter(NativeModules.GoogleNearbyMessages)
  emitters.push(
    eventEmitter.addListener('onMessageFound', onMessageFound),
    eventEmitter.addListener('onMessageLost', onMessageLost),
    eventEmitter.addListener('onActivityStart', onActivityStart),
    eventEmitter.addListener('onActivityStop', onActivityStop),
  );


  return () => {
    emitters.forEach(emitter => emitter.remove());
  };
}

export default {
  start,
  stop,
  registerToEvents,
};