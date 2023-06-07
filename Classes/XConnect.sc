XConnect {
	classvar <>all;
	var <key, <nickname, <oscrouter, <butz, <syncText, <>postRunCode = false, <>postHistory = false;
	var <historyFunc, <doItFunc;
	var peerListWidget, peerListText;
	var joined = false, showGUI = false;
	var myProtocol;
	var <protocols;
	var xoo;
	var <waitingForReply;

	*initClass {
		all = ();
	}

	*new { |key, nickname = "", oscServer=nil|
		var xconnect;

		if (oscServer.isNil) {
			oscServer = NetAddr("gencomp.medienhaus.udk-berlin.de", 55555);
		};

		nickname = if (nickname.asString.isEmpty) { Peer.defaultName } { nickname };

		if (all[key].isNil) { all[key] = () };

		xconnect = all[key][nickname];

		if (xconnect.isNil) {
			xconnect = super.newCopyArgs(key,nickname).initXConnect(oscServer);
			all[key][nickname] = xconnect;
		};

		^xconnect;
	}

	protocol_ { |protocol|
		myProtocol = protocol;
	}

	protocol {
		^myProtocol
	}

	runCode  { arg receive=false;
		if (receive) {
			doItFunc.enable(\runCode);
		} {
			doItFunc.disable(\runCode);
		};
	}

	publishProtocol {
		if (myProtocol.notNil) {
			var actions = myProtocol.actions;
			var proxies = myProtocol.proxies;
			var tasks = myProtocol.tasks;
			var protocol = [];
			if (actions.notNil) {
				actions.keysValuesDo {|key, value|
					value.xpublish(key, xoo);
				};
			};

			if (proxies.notNil) {
				proxies.do {|proxy|
					proxy.xpublish(proxy.key, xoo);
				};
			};

			if (tasks.notNil) {
				tasks.do {|task|
					task.xpublish(task.key, xoo);
				};
			};

			if (actions.notNil) {
				protocol = protocol ++ [\actions, actions.keys.size, actions.keys.asList].flatten;
			};
			if (proxies.notNil) {
				protocol = protocol ++ [\proxies, proxies.size, proxies.collect{|proxy| proxy.key}].flatten;
			};
			if (tasks.notNil) {
				protocol = protocol ++ [\tasks, tasks.size, tasks.collect{|task| task.key}].flatten;
			};
			if (protocol.notEmpty) {
				oscrouter.sendMsg('/protocol', oscrouter.userName, *protocol);
			};
		};
	}

	peers {
		^oscrouter.peers.reject {|peer| peer == nickname }.collect {|peer| [peer, XPeer(peer, this)] }.flatten.asEvent;
	}

	initXConnect { |oscServer|
		if (nickname.asString.isEmpty)  {
			"Couldn't set a name automatically, please set your nickname: XConnect.nickname = 'somename';".warn;
		};

		waitingForReply = ();

		historyFunc = MFunc();
		doItFunc = MFunc();

		protocols = ();

		oscrouter = OSCRouterClient(nickname, key, oscServer.hostname, 'xconnect', 'xconnect', oscServer.port);
		oscrouter.join({this.prOnJoin});
		^this;
	}

	prOnJoin {
		joined = true;
		xoo = XOpenObject(this.oscrouter);
		xoo.start;
		xoo.openInterpreter;
		oscrouter.addResp('/oscrouter/userlist', {|msg|
			if (peerListWidget.notNil) {
				defer {
					var peers = this.prGetPeerListToGUI;
					peerListWidget.items = peers;
					peerListText.string = "Peers (%):".format(peers.size);
				};
			};
			this.publishProtocol;
		});

		oscrouter.addResp('/protocol', {|msg|
			var user = msg[1];
			var xpeer = XPeer(user, this);
			var protocol = ();
			var typ, elements;
			msg = msg[2..].reverse.postln;
			while { msg.isEmpty.not } {
				var classTyp;
				typ = msg.pop;
				elements = msg.pop;
				if (typ.isKindOf(Symbol).not) {
					"Protocol fail: '%' is not a Symbol".format(typ).warn;
				};
				if (elements.isKindOf(Integer).not) {
					"Protocol fail: '%' is not a Number".format(typ).warn;
				};
				classTyp = XRemoteObject.getForType(typ.asSymbol);
				elements.do {
					var el = msg.pop;
					if (classTyp.notNil) {
						protocol[typ] = protocol[typ] ++ [el, classTyp.new(el, xpeer)];
					} {
						"Unknown protocol type: %".format(typ).warn;
					};
				};
				protocol[typ] = protocol[typ].asEvent;
			};
			protocols[user] = protocol;
		});

		oscrouter.addResp('/getNdefs', {|msg|
			var user = msg[1].postln;
			if (user != oscrouter.userName) {
				oscrouter.sendPrivate(user, \ndefs, this.listNdefs);
			};
		});


		oscrouter.addPrivateResp(\oo_reply, {|sender, msg|
			var replyID = msg[1];
			var reply = msg[2..];
			"got a reply: % ".format(replyID).post;
			msg.postln;
			if (waitingForReply[replyID].notNil) {
				waitingForReply[replyID].value(reply);
				waitingForReply[replyID] = nil;
			};
		});

		oscrouter.addPrivateResp('/newProxy', {|sender, msg|
			var name = msg[1];
			var source = msg[2];
			if (xoo.prAvoidTheWorst(source)) {
				Ndef(name, source.asString.interpret);
				if (this.protocol.isNil) {
					this.protocol = ();
				};
				this.protocol[\proxies] = this.protocol[\proxies] ++ [Ndef(name)];
				this.publishProtocol;
			};

		});

		oscrouter.addPrivateResp(\ndefs, {|sender, msg|
			var ndefs = msg;
			sender.post;
			": ".post;
			ndefs.postln;
		});

		this.prInitSyncText;
		this.prMakeHistory;

		defer {
			butz = Butz("xconn-%".format(key).asSymbol);
			butz.add(\Peers, {this.showPeersWindow});
			butz.add(\SyncText, {this.syncText.showDoc});
			butz.add(\History, {this.showHistory});
			butz.add(\Public, {this.makePublic});
			butz.add(\Private, {this.makePrivate});
			if (showGUI) {
				this.gui;
				showGUI = false;
			}
		};
	}

	makePublic {
		History.start;
		historyFunc.enable(\share);
		historyFunc.enable(\do_it);
		doItFunc.enable(\runCode);
	}

	makePrivate {
		historyFunc.disable(\share);
		historyFunc.disable(\do_it);
		doItFunc.disable(\runCode);
		History.end;
	}

	prInitSyncText {
		syncText = SyncText(key, oscrouter.userName, oscrouter);
	}

	prGetPeerListToGUI {
		^oscrouter.peers.collect {|peer|
			if (peer.asSymbol == nickname.asSymbol) {
				peer = "*%".format(peer).asSymbol;
			};
			peer;
		}
	}

	showHistory {
		History.makeWin;
	}

	monitorAndPropagate { |addr|
		//oscrouter.addResp()
	}

	gui {
		if (joined) {
			defer {
				Butz.curr = butz.name;
				Butz.show;
			};
		} {
			showGUI = true;
		};
	}

	showPeersWindow {
		defer {
			var peerWin = Window.new("xconn-%-peers".format(key), Rect(100,100,150,200));
			var peers = this.prGetPeerListToGUI;
			peerListWidget = ListView.new(peerWin).items = peers;
			peerListText = StaticText(peerWin).string_("Peers (%):".format(peers.size));
			peerWin.layout = VLayout();
			peerWin.layout.add(peerListText);
			peerWin.layout.add(peerListWidget);
			peerWin.front;
		}
	}

	prMakeHistory {
		History.start;
		OSCdef(\history, { |msg ... args|
			var nameID = msg[1];
			var codeString = msg[2].asString;
			var shoutString;
			History.enter(codeString, nameID);
			if (postHistory) {
				"history message received from % \n".postf(nameID.cs);
				codeString.postcs;
			};
			//
			if (codeString.beginsWith(NMLShout.tag)) {
				shoutString = codeString.split("\n").first.drop(4);
				////// anonymity or better not?
				shoutString = "% : %".format(nameID, shoutString).postln;
				defer { NMLShout(codeString) };
			};
		}, \history).permanent_(true);


		/// Use a Modal function, an MFdef for forwarding:
		historyFunc.add('share', { |code, result|
			"send code to shared history ...".postln;
			oscrouter.sendMsg(\history, oscrouter.userName, code);
		}, false);
		historyFunc.add('do_it', { |code, result|
			"send code to run everywhere ...".postln;
			oscrouter.sendMsg(\do_it, oscrouter.userName, code);
		}, false);


		historyFunc.disable('do_it');

		History.forwardFunc = historyFunc;

		doItFunc.add('runCode', { |msg|
			var who = msg.postcs[1].asString;
			var code = msg[2].asString;

			var isSafe = {
				// code from OpenObject avoidTheWorst method
				code.find("unixCmd").isNil
				and: { code.find("systemCmd").isNil }
				and: { code.find("File").isNil }
				and: { code.find("Pipe").isNil }
				and: { code.find("Public").isNil }
			}.value;

			isSafe.if {
				// defer it so GUI code also always runs
				defer {
					try {
						"do_it: interpreting code ...".postln;
						if (postRunCode) { code.postcs };
						code.interpret
					} {
						(
							"*** oscrouter do_it - code interpret failed:".postln;
							code.cs.keep(100).postln;
						).postln
					}
				}
			} {
				"*** oscrouter do_it unsafe code detected:".postln;
				code.postcs;
			}
		});

		doItFunc.disable('runCode');

		oscrouter.addResp(\do_it, doItFunc);
	}

	listNdefs {
		^Ndef.all.keys.asArray;
	}

	getNdefs {
		oscrouter.sendMsg('/getNdefs', oscrouter.userName);
	}
}


XRemoteObject {
	var <name, <xpeer;

	*getForType {|type|
		^(actions: XAction, tasks: XTask, proxies: XProxy)[type];
	}

	*new {|name, xpeer|
		^super.newCopyArgs(name, xpeer);
	}

	printOn { |stream|
		stream << "%(%)".format(this.class.name, this.name);
	}
}


XJIT : XRemoteObject {
	get {|action ... args|
		var replyID = 9999.rand;
		xpeer.waitForReply(replyID, action);
		this.xpeer.sendMsg('/oo', replyID, this.name, \get, *args);
	}


	/*
	set {|...args|
		this.xpeer.sendMsg('/oo', this.name, \set, *args);
	}



	play {|...args|
		this.xpeer.sendMsg('/oo', this.name, \play, *args);
	}

	stop {|...args|
		this.xpeer.sendMsg('/oo', this.name, \stop, *args);
	}*/

	doesNotUnderstand { arg selector ...args;
		this.xpeer.sendMsg('/oo', this.name, selector, *args);
	}
}

XProxy : XJIT {
	setSource {|func|
		xpeer.sendMsg('/oo_p',  this.name, func.def.sourceCode);
	}
}

XTask : XJIT {

}

XAction : XRemoteObject {
	value {|...args|
		this.xpeer.sendMsg('/oo', this.name, \value, *args);
	}
}

XPeer {
	classvar all;
	var name, xconn;

	*initClass {
		all = ();
	}

	*new {|name, xconn|
		var xpeer;

		if (all[xconn].isNil) {
			all[xconn] = ();
		};

		xpeer = all[xconn][name];

		if (xpeer.isNil) {
			xpeer = super.newCopyArgs(name, xconn);
			all[xconn][name] = xpeer;
		};

		^xpeer;
	}

	waitForReply {|replyID, action|
		xconn.waitingForReply[replyID] = action;
	}

	sendMsg {|...msg|
		xconn.oscrouter.sendPrivate(name, *msg);
	}

	protocol {
		^xconn.protocols[name];
	}

	tasks {
		^this.protocol.tasks;
	}

	proxies {
		^this.protocol.proxies;
	}

	actions {
		^this.protocol.actions;
	}

	addNewProxy {|name, func|
		this.sendMsg('/newProxy', name, func.def.sourceCode);
	}

	/*doesNotUnderstand { arg selector ...args;
	this.sendMsg('/oo', selector, \value, *args);
	}*/
}

XOpenObject {
	classvar <>all;
	var <>oscrouter;
	var <>objects;
	var <responders;
	var <>lookup = false;
	var <>replyPort;
	var <>avoidTheWorst = true;

	*initClass {
		all = ();
	}

	*new {|oscrouter|
		var xoo;
		if (oscrouter.isNil) {
			"Can't create without OSCRouterClient".warn;
			^XOpenObject;
		};

		xoo = all[oscrouter];

		if (xoo.isNil) {
			all[oscrouter] = this.newCopyArgs(oscrouter).init;
			xoo = all[oscrouter];
		}
		^xoo;
	}

	init {
		objects = ();
	}


	addResponder { |cmd, func|
		if (oscrouter.isNil) {
			"*** oscrouter is not set".warn;
			^this;
		};

		oscrouter.addPrivateResp(cmd, { |... msg|
			var res;
			var sender = msg[0];
			msg = msg[1][1..];
			msg.postln;
			// some type matching
			if(msg[0].isNumber) {
				// replyID name selector args ...
				res = func.value(msg[1..]);
				this.sendReply(sender, msg[0], res);
			} {
				// name selector args ...
				func.value(msg[0..])
			}
		});
	}

	sendReply { |to, id, args|
		args = args.asOSCArgArray;
		//addr.sendMsg("/oo_reply", id, *args);
		this.oscrouter.sendPrivate(to.asSymbol, 'oo_reply', id, *args);
	}

	openProxies {
		[Pdef, Pdefn, Tdef, Fdef, Ndef].do { |class| class.xpublish(class.name, this) };
		lookup = true;
	}

	isListening {
		^(this.oscrouter.privateResponderFuncs['/oo'].notNil && this.oscrouter.privateResponderFuncs['/oo_k'].notNil);
	}

	put { |name, object|
		objects.put(name, object)
	}

	keyFor { |object|
		^objects.findKeyForValue(object)
	}

	remove { |object|
		var key = this.keyFor(object);
		key !? { objects.removeAt(key) }
	}

	removeAt { |name|
		^objects.removeAt(name)
	}


	start {
		if(this.isListening) { "OpenObject: already listening".warn; ^this };

		this.addResponder('/oo', { |msg| this.oscPerform(msg) });
		this.addResponder('/oo_k', { |msg| this.oscPerformKeyValuePairs(msg) });

	}

	clear {
		oscrouter.removeResp('/oo');
		oscrouter.removeResp('/oo_k');
		oscrouter.removeResp('/oo_i');
		oscrouter.removeResp('/oo_p');
		objects = ();
		all[oscrouter] = nil;
	}

	// a dangerous tool for both good and evil ...

	openInterpreter {
		("Networking opens interpreter - use 'openInterpreter' at your own risk"
			"\nuse 'closeInterpreter' to close interpreter. *").postln;
		this.addResponder('/oo_p', { |msg| this.setProxySource(msg) });
		this.addResponder('/oo_i', { |msg| this.interpretOSC(msg) });
	}

	// safe again.

	*closeInterpreter {
		this.removeResponder('/oo_i');
		this.removeResponder('/oo_p');
		this.removeResponder('/oor_i');
	}


	////////////// private implementation /////////////////

	removeResponder { |cmd|
		oscrouter.removeResp(cmd);
	}

	// if lookup == true, use "name_key" lookup scheme
	getObject { |name|
		var object, objectName, key;
		^objects.at(name) ?? {
			if(lookup) {
				#objectName ... key = name.asString.split($_);
				object = objects.at(objectName.asSymbol);
				if(object.isNil) { ^nil };
				object.at(key.join($_).asSymbol);
			}
		}
	}

	// name, selector, args ...
	oscPerform { |msg|
		var name, selector, args, receiver;
		#name, selector ... args = msg;
		receiver = this.getObject(name);
		^if(receiver.isNil) {
			"OpenObject: name: % not found".format(name).warn;
			nil
		} {
			args = args.unfoldOSCArrays;
			receiver.performList(selector, args)
		}
	}

	// name, selector, argName1, val1, argName2, val2 ...

	oscPerformKeyValuePairs { |msg|
		var name, selector, args, receiver;
		#name, selector ... args = msg;
		receiver = this.getObject(name);
		^if(receiver.isNil) {
			"OpenObject: name: % not found".format(name).warn;
			nil
		} {
			args = args.unfoldOSCArrays;
			receiver.performKeyValuePairs(selector, args)
		}
	}

	// name, sourceCode

	setProxySource { |msg|

		/*msg.pairsDo { |name, string|
		var object, receiver;
		receiver = this.getObject(name);
		string.postcs;

		if(receiver.isNil) {
		"OpenObject: name: % not found".format(name).warn;
		} {
		object = string.asString.interpret;
		object !? { receiver.source_(object) };
		}
		};*/

		// for now, support only single sets

		var name = msg[0], string = msg[1..].join;
		var object, receiver, ok = 0;
		receiver = this.getObject(name);
		string.postcs;

		if(receiver.isNil) {
			"OpenObject: name: % not found".format(name).warn;
		} {
			object = string.interpret;
			object !? { receiver.source_(object); ok = 1; };
		}

		^ok
	}

	// evaluate an array of strings and return the results

	interpretOSC { |msg|
		if(this.prAvoidTheWorst(msg) ){
			msg = msg.join;
			^try {
				msg.interpret
			} {
				"\n // % - could not interpret: \n%\n\n".postf(this, msg.cs);
				nil;
			};
		}{
			"Sorry! unixCmds, pipes are not allowed!".warn
		}
	}


	prAvoidTheWorst { arg obj;
		var str;
		if(avoidTheWorst) {
			str = obj.asString;
			^str.find("unixCmd").isNil
			and: { str.find("systemCmd").isNil }
			and: { str.find("File").isNil }
			and: { str.find("Pipe").isNil }
			and: { str.find("Public").isNil }
		} {
			^true;
		}
	}

}




+ Object {

	xpublish { |name, xoo=nil|
		if (xoo.isNil){
			XOpenObject.all.values.do {|xoo|
				if(xoo.objects.at(name).notNil) {
					"XOpenObjects: overriding object with this name: %".format(name).warn
				};
				xoo.put(name, this);
			};
		} {
			if(xoo.objects.at(name).notNil) {
				"XOpenObjects: overriding object with this name: %".format(name).warn
			};
			xoo.put(name, this);
		};
	}

	xunpublish { |name, xoo=nil|
		if (xoo.isNil) {
			XOpenObject.all.values.do {|xoo|
				xoo.remove(this);
			};
		} {
			xoo.remove(this)
		};
	}

}