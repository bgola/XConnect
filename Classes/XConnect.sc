XConnect {
	classvar <>all, <>nickname="";
	var <key, <oscrouter, <butz;
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
		nickname = if (nickname.asString.isEmpty) { XConnect.defaultName } { nickname };

		if (nickname.asString.isEmpty)  {
			"Couldn't set a name automatically, please set your nickname: XConnect.nickname = 'somename';".warn;
		};

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
		butz = Butz("xconn-%".format(key).asSymbol);
		butz.add(\Peers, {this.showPeersWindow});
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

	// From Utopia Quark
	*defaultName {
        var cmd, name;
        cmd = Platform.case(
                \osx,       { "id -un" },
                \linux,     { "id -un" },
                \windows,   { "echo %username%" },
            {""}
        );
        name = cmd.unixCmdGetStdOut;
        if(name.size == 0, { name ="hostname".unixCmdGetStdOut });
        name = name.replace("\n", "");
        ^name;
    }

}