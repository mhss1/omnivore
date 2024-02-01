package app.omnivore.omnivore.ui

import app.omnivore.omnivore.dataService.DataService
import app.omnivore.omnivore.graphql.generated.type.CreateLabelInput
import app.omnivore.omnivore.graphql.generated.type.SetLabelsInput
import app.omnivore.omnivore.network.Networker
import app.omnivore.omnivore.network.updateLabelsForSavedItem
import app.omnivore.omnivore.persistence.entities.SavedItemAndSavedItemLabelCrossRef
import app.omnivore.omnivore.persistence.entities.SavedItemLabel
import com.apollographql.apollo3.api.Optional



suspend fun setSavedItemLabels(
    networker: Networker,
    dataService: DataService,
    savedItemID: String,
    labels: List<SavedItemLabel>
): Boolean {
    val input = SetLabelsInput(
        pageId = savedItemID,
        labels = Optional.presentIfNotNull(labels.map { CreateLabelInput(color = Optional.presentIfNotNull(it.color), name = it.name) }),
    )

    val updatedLabels = networker.updateLabelsForSavedItem(input)

    // Figure out which of the labels are new
    updatedLabels?.let { updatedLabels ->
        val existingNamedLabels = dataService.db.savedItemLabelDao()
            .namedLabels(updatedLabels.map { it.labelFields.name })
        val existingNames = existingNamedLabels.map { it.name }
        val newNamedLabels = updatedLabels.filter { !existingNames.contains(it.labelFields.name) }

        dataService.db.savedItemLabelDao().insertAll(newNamedLabels.map {
            SavedItemLabel(
                savedItemLabelId = it.labelFields.id,
                name = it.labelFields.name,
                color = it.labelFields.color,
                createdAt = null,
                labelDescription = null
            )
        })

        val allNamedLabels = dataService.db.savedItemLabelDao()
            .namedLabels(updatedLabels.map { it.labelFields.name })
        val crossRefs = allNamedLabels.map {
            SavedItemAndSavedItemLabelCrossRef(
                savedItemLabelId = it.savedItemLabelId,
                savedItemId = savedItemID
            )
        }
        dataService.db.savedItemAndSavedItemLabelCrossRefDao().deleteRefsBySavedItemId(savedItemID)
        dataService.db.savedItemAndSavedItemLabelCrossRefDao().insertAll(crossRefs)

        return true
    } ?: run {
        return false
    }
}
