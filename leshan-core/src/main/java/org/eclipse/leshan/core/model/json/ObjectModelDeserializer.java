/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.core.model.json;

import java.lang.reflect.Type;

import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class ObjectModelDeserializer implements JsonDeserializer<ObjectModel> {

    @Override
    public ObjectModel deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null) {
            return null;
        }

        if (!json.isJsonObject()) {
            return null;
        }

        final JsonObject jsonObject = json.getAsJsonObject();
        if (!jsonObject.has("id")) {
            return null;
        }

        final int id = jsonObject.get("id").getAsInt();
        final String name = jsonObject.get("name").getAsString();
        final String instancetype = jsonObject.get("instancetype").getAsString();
        final boolean mandatory = jsonObject.get("mandatory").getAsBoolean();
        final String description = jsonObject.get("description").getAsString();
        final String version = jsonObject.has("version") ? jsonObject.get("version").getAsString() : ObjectModel.DEFAULT_VERSION;
        final ResourceModel[] resourceSpecs = context.deserialize(jsonObject.get("resourcedefs"), ResourceModel[].class);

        return new ObjectModel(id, name, description, version, "multiple".equals(instancetype), mandatory, resourceSpecs);
    }
}
