/**
 * (C) Copyright 2013 Jabylon (http://www.jabylon.org) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jabylon.rest.ui.model;

import org.apache.wicket.model.IModel;
import org.eclipse.emf.cdo.CDOObject;
import org.eclipse.emf.ecore.EStructuralFeature;

public class EObjectModel<T extends CDOObject> extends AbstractEMFModel<T, T> implements IEObjectModel<T> {

    public EObjectModel(T model)
    {
        super(model);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public T getObject()
    {
        return getDomainObject();
    }

    @Override
    public void setObject(T object)
    {
        setDomainObject(object);
    }

    /* (non-Javadoc)
     * @see org.jabylon.rest.ui.model.IEObjectModel#forProperty(org.eclipse.emf.ecore.EStructuralFeature)
     */
    @Override
    public <X> IModel<X> forProperty(EStructuralFeature feature)
    {
        return new EObjectPropertyModel<X, T>(this, feature);
    }

}
