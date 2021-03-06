/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiUIView.h"
#import "TiWindowProxy.h"
#import "KrollBridge.h"
#import "TiViewProxy.h"

//TODO: we probably should split this ViewProxy into a a separate TiUIView like normal

@interface TiUIWindowProxy : TiWindowProxy 
{
@private
	KrollBridge *context;
	BOOL hasToolbar;
	BOOL contextReady;
}

-(void)_refreshBackButton;

@end
