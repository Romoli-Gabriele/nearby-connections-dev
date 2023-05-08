//
//  Connectins.swift
//  native1
//
//  Created by Gabriele Romoli on 02/05/23.
//

import Foundation
import NearbyConnections
import CoreBluetooth
import NearbyCoreAdapter
import CoreLocation
import UserNotifications
import BackgroundTasks
import Herald
import os

class MySupplier: PayloadDataSupplier{
  var message: String
  init(message:String) {
    self.message = message
  }
  func payload(_ timestamp: Herald.PayloadTimestamp, device: Herald.Device?) -> Herald.PayloadData? {
    let buf = message.data(using: .utf8)
    let payload: PayloadData = PayloadData(buf!)
    return payload
  }
}

@objc(NearbyMessages)
class Example : RCTEventEmitter, CLLocationManagerDelegate, SensorDelegate{
  
  
  var Messages: Array<String>;
  
  //HERALD
  private let logger = Log(subsystem: "Herald", category: "AppDelegate")
  var sensor: SensorArray?
  var phoneMode = true
  let automatedTestServer: String? = nil
  var payloadDataSupplier: PayloadDataSupplier?
  var automatedTestClient: AutomatedTestClient? = nil
  private var foreground: Bool = true
  
  
  
  // MARK:- Events
  private var didDetect = 0
  private var didRead = 0
  private var didMeasure = 0
  private var didShare = 0
  private var didReceive = 0
  
  
  
  private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskIdentifier.invalid
  private var threadStarted = false
  private var threadShouldExit = false
  
  
  @objc
  func start(_ message: String){
    DispatchQueue.main.async {
      NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
        self.active = true
      }
      NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
        self.active = false
      }
    }
    
    //discoverer.startDiscovery()
    print("PARTITO")
    print(self.Messages)
    //advertiser.startAdvertising(using: message.data(using: .utf8)!)
    phoneMode = true
    payloadDataSupplier = MySupplier(message: message)
    self.sensor = SensorArray(payloadDataSupplier!)
    self.sensor?.add(delegate: self)
    self.sensor?.start()
    
    
    locationManager.pausesLocationUpdatesAutomatically = false
    locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
    locationManager.distanceFilter = 3000.0
    locationManager.showsBackgroundLocationIndicator = true;
    if #available(iOS 9.0, *) {
      locationManager.allowsBackgroundLocationUpdates = true
    } else {
      // not needed on earlier versions
    }
    // start updating location at beginning just to give us unlimited background running time
    self.locationManager.startUpdatingLocation()
    
    //periodicallySendScreenOnNotifications(message:message)
    extendBackgroundRunningTime()
    
    
    self.sendEvent(withName: EventType.onActivityStart.rawValue, body: "Success")
  }
  
  @objc(stop)
  func stop(){
    self.Messages = Array();
    self.locationManager.stopUpdatingLocation();
    self.threadShouldExit = true
    self.active = false
    UIApplication.shared.endBackgroundTask(self.backgroundTask)
    //advertiser.stopAdvertising()
    self.sensor?.stop()
    self.sensor = nil
    phoneMode = false
    //discoverer.stopDiscovery()
    self.sendEvent(withName: EventType.onActivityStop.rawValue, body: "Success")
  }
  
  private func extendBackgroundRunningTime() {
    if (threadStarted) {
      // if we are in here, that means the background task is already running.
      // don't restart it.
      return
    }
    threadStarted = true
    NSLog("Attempting to extend background running time")
    
    self.backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Task1", expirationHandler: {
      NSLog("Background task expired by iOS.")
      UIApplication.shared.endBackgroundTask(self.backgroundTask)
    })
    
    
    var lastLogTime = 0.0
    DispatchQueue.global().async {
      let startedTime = Int(Date().timeIntervalSince1970) % 10000000
      NSLog("*** STARTED BACKGROUND THREAD")
      while(!self.threadShouldExit) {
        DispatchQueue.main.async {
          let now = Date().timeIntervalSince1970
          let backgroundTimeRemaining = UIApplication.shared.backgroundTimeRemaining
          if abs(now - lastLogTime) >= 2.0 {
            lastLogTime = now
            if backgroundTimeRemaining < 10.0 {
              NSLog("About to suspend based on background thread running out.")
            }
            if (backgroundTimeRemaining < 200000.0) {
              NSLog("Thread \(startedTime) background time remaining: \(backgroundTimeRemaining)")
            }
            else {
              //NSLog("Thread \(startedTime) background time remaining: INFINITE")
            }
          }
        }
        sleep(1)
      }
      self.threadStarted = false
      NSLog("*** EXITING BACKGROUND THREAD")
    }
    
  }
  
  private func periodicallySendScreenOnNotifications(message: String) {
    DispatchQueue.global().asyncAfter(deadline: DispatchTime.now()+30.0) {
      self.advertiser.stopAdvertising()
      self.advertiser = Advertiser(connectionManager: self.connectionManager)
      self.advertiser.startAdvertising(using: message.data(using: .utf8)!)
      self.periodicallySendScreenOnNotifications(message: message)
    }
  }
  
  
  
  enum EventType: String, CaseIterable {
    case onMessageFound
    case onMessageLost
    case onActivityStart
    case onActivityStop
  }
  
  enum GoogleNearbyMessagesError: Error, LocalizedError {
    case permissionError(permissionName: String)
    case runtimeError(message: String)
    
    
    public var errorDescription: String? {
      switch self {
      case .permissionError(permissionName: let permissionName):
        return "Permission has been denied! Denied Permission: \(permissionName). Make sure to include NSBluetoothPeripheralUsageDescription in your Info.plist!"
      case .runtimeError(message: let message):
        return message
      }
    }
  }
  
  override func supportedEvents() -> [String]! {
    return EventType.allCases.map { (event: EventType) -> String in
      return event.rawValue
    }
  }
  override static func requiresMainQueueSetup() -> Bool {
    // init on main thread, audio doesn't work on background thread.
    return true
  }
  private var active = true
  private var tempBluetoothManager: CBCentralManager? = nil
  private var tempBluetoothManagerDelegate: CBCentralManagerDelegate? = nil
  private var _locationManager: CLLocationManager?
  private var didCallback = false
  let connectionManager: ConnectionManager
  var advertiser: Advertiser
  let discoverer: Discoverer
  public var locationManager: CLLocationManager {
    get {
      if let l = _locationManager {
        return l
      }
      else {
        let l = CLLocationManager()
        l.delegate = self
        _locationManager = l
        return l
      }
    }
  }
  override init() {
    
    connectionManager = ConnectionManager(serviceID: "SPOTLIVE", strategy: .cluster)
    advertiser = Advertiser(connectionManager: connectionManager)
    discoverer = Discoverer(connectionManager: connectionManager)
    Messages = Array()
    super.init();
    discoverer.delegate = self
    connectionManager.delegate = self
    advertiser.delegate = self
  }
  @objc(checkBluetoothPermission:rejecter:)
  func checkBluetoothPermission(_ resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) -> Void {
    print("GNM_BLE: Checking Bluetooth Permissions...")
    let hasBluetoothPermission = self.hasBluetoothPermission()
    resolve(hasBluetoothPermission)
  }
  @objc(checkBluetoothAvailability:rejecter:)
  func checkBluetoothAvailability(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    let notificationCenter = UNUserNotificationCenter.current();
    notificationCenter.requestAuthorization(options: [.badge, .alert, .sound]) {
      (granted, error) in
      if(error == nil)
      {
        print("Accettate notifiche: \(granted)")
      }
    }
    locationManager.requestAlwaysAuthorization()
    if (self.tempBluetoothManager != nil || self.tempBluetoothManagerDelegate != nil) {
      let error = GoogleNearbyMessagesError.runtimeError(message: "Another Bluetooth availability check is already in progress!")
      reject("GOOGLE_NEARBY_MESSAGES_CHECKBLUETOOTH_ERROR", error.localizedDescription, error)
      return
    }
    self.didCallback = false
    class BluetoothManagerDelegate : NSObject, CBCentralManagerDelegate {
      private var promiseResolver: RCTPromiseResolveBlock
      private weak var parentReference: Example?
      init(resolver: @escaping RCTPromiseResolveBlock, parentReference: Example) {
        self.promiseResolver = resolver
        self.parentReference = parentReference
      }
      
      func centralManagerDidUpdateState(_ central: CBCentralManager) {
        guard let parent = parentReference else {
          return
        }
        if (!parent.didCallback) {
          parent.didCallback = true
          print("GNM_BLE: CBCentralManager did update state with \(central.state.rawValue)")
          self.promiseResolver(central.state == .poweredOn)
          parent.tempBluetoothManager = nil
          parent.tempBluetoothManagerDelegate = nil
        }
      }
    }
    tempBluetoothManagerDelegate = BluetoothManagerDelegate(resolver: resolve, parentReference: self)
    tempBluetoothManager = CBCentralManager(delegate: tempBluetoothManagerDelegate, queue: nil)
    
    DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(10)) {
      if (!self.didCallback) {
        self.didCallback = true
        let error = GoogleNearbyMessagesError.runtimeError(message: "The CBCentralManager (Bluetooth) did not power on after 10 seconds. Cancelled execution.")
        reject("GOOGLE_NEARBY_MESSAGES_CHECKBLUETOOTH_TIMEOUT", error.localizedDescription, error)
        self.tempBluetoothManager = nil
        self.tempBluetoothManagerDelegate = nil
      }
    }
  }
  
  func hasBluetoothPermission() -> Bool {
    if #available(iOS 13.1, *) {
      return CBCentralManager.authorization == .allowedAlways
    } else if #available(iOS 13.0, *) {
      return CBCentralManager().authorization == .allowedAlways
    }
    // Before iOS 13, Bluetooth permissions are not required
    return true
  }
  
  
  
  // HERALD IMPLEMENT
  func sensor(_ sensor: SensorType, didDetect: TargetIdentifier) {
      self.didDetect += 1
      guard foreground else {
          return
      }
      DispatchQueue.main.async {
          print(self.didDetect)
      }
  }

  func sensor(_ sensor: SensorType, didRead: PayloadData, fromTarget: TargetIdentifier) {
    print("qualcosa")
      self.didRead += 1
    let newDevice = String(decoding: didRead.data, as: UTF8.self);
    if(!self.Messages.contains(newDevice)){
      self.Messages.append(newDevice);
      self.sendEvent(withName: EventType.onMessageFound.rawValue, body: newDevice)
    }
  }

  func sensor(_ sensor: SensorType, didShare: [PayloadData], fromTarget: TargetIdentifier) {
    print("qualcosa")
    for share in didShare {
      let newDevice = String(decoding: share.data, as: UTF8.self);
      if(!self.Messages.contains(newDevice)){
        self.Messages.append(newDevice);
        self.sendEvent(withName: EventType.onMessageFound.rawValue, body: newDevice)
      }
    }
  }

  func sensor(_ sensor: SensorType, didMeasure: Proximity, fromTarget: TargetIdentifier) {
    //
  }

  // Immediate send data (text in demo app), NOT payload data
  func sensor(_ sensor: SensorType, didReceive: Data, fromTarget: TargetIdentifier) {
    let newDevice = String(decoding: didReceive, as: UTF8.self);
    if(!self.Messages.contains(newDevice)){
      self.Messages.append(newDevice);
      self.sendEvent(withName: EventType.onMessageFound.rawValue, body: newDevice)
    }
  }
}



extension Example: ConnectionManagerDelegate {
  func connectionManager(
    _ connectionManager: ConnectionManager, didReceive verificationCode: String,
    from endpointID: EndpointID, verificationHandler: @escaping (Bool) -> Void) {
    // Optionally show the user the verification code. Your app should call this handler
    // with a value of `true` if the nearby endpoint should be trusted, or `false`
    // otherwise.
    verificationHandler(true)
  }

  func connectionManager(
    _ connectionManager: ConnectionManager, didReceive data: Data,
    withID payloadID: PayloadID, from endpointID: EndpointID) {
    // A simple byte payload has been received. This will always include the full data.
  }

  func connectionManager(
    _ connectionManager: ConnectionManager, didReceive stream: InputStream,
    withID payloadID: PayloadID, from endpointID: EndpointID,
    cancellationToken token: CancellationToken) {
    // We have received a readable stream.
  }

  func connectionManager(
    _ connectionManager: ConnectionManager,
    didStartReceivingResourceWithID payloadID: PayloadID,
    from endpointID: EndpointID, at localURL: URL,
    withName name: String, cancellationToken token: CancellationToken) {
    // We have started receiving a file. We will receive a separate transfer update
    // event when complete.
  }

  func connectionManager(
    _ connectionManager: ConnectionManager,
    didReceiveTransferUpdate update: TransferUpdate,
    from endpointID: EndpointID, forPayload payloadID: PayloadID) {
    // A success, failure, cancelation or progress update.
  }

  func connectionManager(
    _ connectionManager: ConnectionManager, didChangeTo state: ConnectionState,
    for endpointID: EndpointID) {
    switch state {
    case .connecting:
      print("A connection to the remote endpoint is currently being established.")
    case .connected:
      print("We're connected! Can now start sending and receiving data.")
    case .disconnected:
      print("We've been disconnected from this endpoint. No more data can be sent or received.")
    case .rejected:
      print("The connection was rejected by one or both sides.")
    }
  }
}
extension Example: AdvertiserDelegate {
  func advertiser(
    _ advertiser: Advertiser, didReceiveConnectionRequestFrom endpointID: EndpointID,
    with context: Data, connectionRequestHandler: @escaping (Bool) -> Void) {
      let endpoint = DiscoveredEndpoint(
          id: endpointID,
          endpointName: String(data: context, encoding: .utf8)!
      )
    connectionRequestHandler(true)
  }
}
extension Example: DiscovererDelegate {
  func discoverer(
    _ discoverer: Discoverer, didFind endpointID: EndpointID, with context: Data) {
      let endpoint = DiscoveredEndpoint(
          id: endpointID,
          endpointName: String(data: context, encoding: .utf8)!
      )
    print("An endpoint was found.")
    print(endpoint)
      self.sendEvent(withName: EventType.onMessageFound.rawValue, body: endpoint.endpointName)
  }

  func discoverer(_ discoverer: Discoverer, didLose endpointID: EndpointID) {
    print("A previously discovered endpoint has gone away.")
  }
}
struct DiscoveredEndpoint: Identifiable {
    let id: EndpointID
    let endpointName: String
}
