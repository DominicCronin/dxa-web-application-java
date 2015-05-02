/*
 * Copyright (c) 2015 SDL, Radagio & R. Oudshoorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dd4t.contentmodel;

import org.dd4t.core.util.TCMURI;

import java.util.List;

/**
 * Interface for Keyword item
 *
 * @author Quirijn Slings, Mihai Cadariu
 */
public interface Keyword extends Item, HasMetadata {

    public String getDescription();

    public String getKey();

    public List<Keyword> getChildKeywords();

    public boolean hasChildren();

    public List<Keyword> getParentKeywords();

    public boolean hasParents();

    public List<TCMURI> getRelatedKeywords();

    public boolean hasRelatedKeywords();

    public String getPath();

    public void setPath(String path);

    public List<TCMURI> getClassifiedItems();

    public boolean hasClassifiedItems();

    public String getTaxonomyId();

    public void setTaxonomyId(String taxonomyId);
}
