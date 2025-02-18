package org.light.collect.treemappingmobile.injection;

import android.arch.lifecycle.ViewModel;

import org.light.collect.treemappingmobile.injection.config.architecture.ViewModelKey;
import org.light.collect.treemappingmobile.injection.config.scopes.PerViewModel;
import org.light.collect.treemappingmobile.location.GeoViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * Module for binding injectable ViewModels.
 * <p>
 * To add a new ViewModel, just copy the format for GeoViewModel below, and Dagger will make sure
 * its injected into your injectable Activity's (see {@link ActivityBuilder}) `@Inject` annotated
 * ViewModel subclass field.
 */
@Module
public abstract class ViewModelBuilder {

    @Binds
    @IntoMap
    @ViewModelKey(GeoViewModel.class)
    @PerViewModel
    abstract ViewModel bindGeoViewModel(GeoViewModel geoViewModel);
}
