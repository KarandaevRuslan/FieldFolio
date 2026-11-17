package com.karandaev.fieldfolio.feature.auth.state

import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.karandaev.fieldfolio.core.auth.firebase.FirebaseAuthAccountManager
import com.karandaev.fieldfolio.core.auth.firebase.FirebaseGoogleAuthProvider
import com.karandaev.fieldfolio.core.domain.repository.PersistedAppStateRepository
import com.karandaev.fieldfolio.core.image.crop.ImageCropper
import com.karandaev.fieldfolio.core.image.sourcefile.ImageSourceFileProvider
import com.karandaev.fieldfolio.core.inspectionapi.InspectionProvider
import com.karandaev.fieldfolio.core.ui.state.viewModelFactory

/** Creates an [AuthViewModel]. */
fun AuthViewModel.Companion.factory(
  auth: FirebaseAuth,
  firebaseGoogleAuthProvider: FirebaseGoogleAuthProvider,
  persistedAppStateRepository: PersistedAppStateRepository,
  inspectionProvider: InspectionProvider,
  imageSourceFileProvider: ImageSourceFileProvider,
  imageCropper: ImageCropper,
): ViewModelProvider.Factory {
  return viewModelFactory {
    AuthViewModel(
      auth = auth,
      authAccountManager =
        FirebaseAuthAccountManager(
          auth = auth,
          firebaseGoogleAuthProvider = firebaseGoogleAuthProvider,
        ),
      persistedAppStateRepository = persistedAppStateRepository,
      inspectionProvider = inspectionProvider,
      imageSourceFileProvider = imageSourceFileProvider,
      imageCropper = imageCropper,
    )
  }
}
