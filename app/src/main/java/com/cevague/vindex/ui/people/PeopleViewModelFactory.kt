package com.cevague.vindex.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cevague.vindex.data.repository.PersonRepository

class PeopleViewModelFactory(private val repository: PersonRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PeopleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PeopleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}