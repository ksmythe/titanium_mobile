/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#import "TiProxy.h"
#import "GDataXMLNode.h"

@interface TiDOMDocumentProxy : TiProxy {
@private
	GDataXMLDocument *document;
}

-(void)parse:(NSURL*)url;
-(void)parseString:(NSString*)xml;

@end
