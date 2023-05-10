XConnect {
	classvar <>all, <>nickname="";
	var <key, <oscrouter, <butz, <syncText, <>postRunCode = false, <>postHistory = false;
	var historyFunc, doItFunc;
	var peerListWidget, peerListText;

	*initClass {
		all = ();
	}

	*new { |key, oscServer=nil|
		var xconnect;

		if (oscServer.isNil) {
			oscServer = NetAddr("gencomp.medienhaus.udk-berlin.de", 55555);
		};

		xconnect = all[key];

		if (xconnect.isNil) {
			xconnect = super.newCopyArgs(key).initXConnect(oscServer);
			all[key] = xconnect;
		};

		^xconnect;
	}

	initXConnect { |oscServer|
		nickname = if (nickname.asString.isEmpty) { Peer.defaultName } { nickname };

		if (nickname.asString.isEmpty)  {
			"Couldn't set a name automatically, please set your nickname: XConnect.nickname = 'somename';".warn;
		};

		historyFunc = MFunc();
		doItFunc = MFunc();

		oscrouter = OSCRouterClient(nickname, key, oscServer.hostname, 'xconnect', 'xconnect', oscServer.port);
		oscrouter.join({this.prOnJoin});
		^this;
	}

	prOnJoin {
		oscrouter.addResp('/oscrouter/userlist', {|msg|
			if (peerListWidget.notNil) {
				defer {
					var peers = this.prGetPeerListToGUI;
					peerListWidget.items = peers;
					peerListText.string = "Peers (%):".format(peers.size);
				};
			};
		});
		this.prInitSyncText;
		this.prMakeHistory;
		butz = Butz("xconn-%".format(key).asSymbol);
		butz.add(\Peers, {this.showPeersWindow});
		butz.add(\SyncText, {this.syncText.showDoc});
		butz.add(\History, {this.showHistory});
		butz.add(\Public, {this.makePublic});
		butz.add(\Private, {this.makePrivate});
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
		Butz.curr = butz.name;
		Butz.show;
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
}