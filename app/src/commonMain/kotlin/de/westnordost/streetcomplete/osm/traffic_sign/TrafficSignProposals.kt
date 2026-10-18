package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.osm.Tags

/* The tags that follow from the signs, other than traffic_sign itself, as proposals the user can
 * accept or decline as a unit (a group of changes that belong together). The rules that derive them
 * are the ones of deriveWayTags (WAY_RULES, which are checked against the catalogue by a test). This
 * file only groups and classifies the resulting changes, it has no rules of its own. */

/** Which kind of rule a proposal belongs to */
enum class ProposalFamily {
    /** DE:237, 239, 240, 241: highway, foot, bicycle, segregated, bicycle:signed */
    PATH,
    /** DE:250, 260 (with exceptions): a ban for vehicle or motor_vehicle */
    BAN,
    /** DE:274.1: maxspeed, zone:maxspeed. Not pre-selected: that the sign applies to the whole way
     *  (and not only to a part of it) is not known */
    ZONE,
}

enum class ProposalKind {
    /** only adds tags (or changes the way type that the sign defines). Pre-selected */
    NEW,
    /** overwrites an existing value that contradicts the sign. Not pre-selected */
    CONFLICT,
    /** removes existing tags, e.g. bicycle=yes below a ban for all vehicles that it would lift.
     *  Not pre-selected, only removed on explicit confirmation */
    REMOVAL,
}

/** A group of changes that are only done together or not at all */
data class TagProposal(
    /** e.g. "path", "ban", "ban:forward", "remove:bicycle" */
    val id: String,
    val kind: ProposalKind,
    val family: ProposalFamily,
    /** null: both directions (or not directional) */
    val direction: TrafficSignDirection?,
    val changes: List<TrafficSignTagChange>,
    /** id of the proposal that has to be accepted for this one to make sense, if any: tags that
     *  would be lifted by a ban are only removed together with the ban */
    val dependsOn: String?,
    /** whether the way type (highway) is changed */
    val changesWayType: Boolean,
) {
    /** Describes exactly what this proposal does to which tags with which old and new values. A
     *  confirmation is only valid for the same fingerprint: if the tags or the values change after
     *  the user confirmed, the proposal is a different one and needs a new confirmation. */
    val fingerprint: String get() = changes.sortedBy { it.key }.joinToString("|") {
        when (it) {
            is TrafficSignTagChange.Set -> "S ${it.key}=${it.previousValue}>${it.value}"
            is TrafficSignTagChange.Remove -> "R ${it.key}=${it.previousValue}"
        }
    }

    val isPreselected: Boolean get() = kind == ProposalKind.NEW && family != ProposalFamily.ZONE
}

private val ZONE_KEYS = setOf("maxspeed", "zone:maxspeed")
private val PATH_GLOBAL_KEYS = setOf("highway", "segregated")
private val PATH_ACCESS_KEYS = setOf("foot", "bicycle")

private fun scopeOf(key: String): TrafficSignDirection? {
    val segments = key.split(':')
    return TrafficSignDirection.entries.firstOrNull { it.osmSuffix in segments.drop(1) }
}

private fun scopeId(direction: TrafficSignDirection?) = direction?.let { ":${it.osmSuffix}" }.orEmpty()

/** Groups the changes of the tags (not the ones of traffic_sign) into proposals.
 *  [familiesOf] tells which kinds of rules are at work for a direction (null: for both) */
internal fun buildProposals(
    changes: List<TrafficSignTagChange>,
    familiesOf: (TrafficSignDirection?) -> Set<ProposalFamily>,
): List<TagProposal> {
    val path = ArrayList<TrafficSignTagChange>()
    val zone = ArrayList<TrafficSignTagChange>()
    val banSets = LinkedHashMap<TrafficSignDirection?, MutableList<TrafficSignTagChange>>()
    val removals = ArrayList<Pair<TrafficSignDirection?, TrafficSignTagChange.Remove>>()

    for (change in changes) {
        val key = change.key
        if (key in TRAFFIC_SIGN_KEYS_FOR_PROPOSALS) continue
        val scope = scopeOf(key)
        val families = familiesOf(scope)
        val base = key.substringBefore(':')
        val isPath = key in PATH_GLOBAL_KEYS || key.startsWith("bicycle:signed") ||
            (ProposalFamily.PATH in families && base in PATH_ACCESS_KEYS) ||
            (families.isNotEmpty() && ProposalFamily.BAN !in families && ProposalFamily.ZONE !in families)
        when {
            ProposalFamily.ZONE in families && key in ZONE_KEYS -> zone.add(change)
            isPath -> path.add(change)
            change is TrafficSignTagChange.Set -> banSets.getOrPut(scope) { ArrayList() }.add(change)
            change is TrafficSignTagChange.Remove -> removals.add(scope to change)
        }
    }

    val result = ArrayList<TagProposal>()
    if (path.isNotEmpty()) {
        val kind = when {
            path.any { it is TrafficSignTagChange.Remove } -> ProposalKind.REMOVAL
            path.any { it is TrafficSignTagChange.Set && it.previousValue != null && it.key != "highway" } -> ProposalKind.CONFLICT
            else -> ProposalKind.NEW
        }
        result.add(TagProposal("path", kind, ProposalFamily.PATH, null, path, null, path.any { it.key == "highway" }))
    }
    if (zone.isNotEmpty()) {
        val kind = if (zone.any { it is TrafficSignTagChange.Set && it.previousValue != null }) ProposalKind.CONFLICT else ProposalKind.NEW
        result.add(TagProposal("zone", kind, ProposalFamily.ZONE, null, zone, null, false))
    }
    val banIds = HashMap<TrafficSignDirection?, String>()
    for ((scope, sets) in banSets) {
        val id = "ban" + scopeId(scope)
        banIds[scope] = id
        val kind = if (sets.any { it is TrafficSignTagChange.Set && it.previousValue != null }) ProposalKind.CONFLICT else ProposalKind.NEW
        result.add(TagProposal(id, kind, ProposalFamily.BAN, scope, sets, null, false))
    }
    for ((scope, removal) in removals) {
        result.add(TagProposal("remove:${removal.key}", ProposalKind.REMOVAL, ProposalFamily.BAN, scope, listOf(removal), banIds[scope], false))
    }
    return result
}

private val TRAFFIC_SIGN_KEYS_FOR_PROPOSALS = setOf("traffic_sign", "traffic_sign:forward", "traffic_sign:backward")

/** What of a [TrafficSignWayTagging] is done, given what the user confirmed */
class ConfirmedChanges(
    /** all changes that are written: the ones of traffic_sign, always, and the accepted proposals */
    val applied: List<TrafficSignTagChange>,
    /** proposals that are not done: declined, not confirmed (for the same fingerprint), or without
     *  the proposal they depend on */
    val skipped: List<TagProposal>,
    /** removals that are not done below a ban that is done: tags that still allow what the ban
     *  forbids. To be shown to the user */
    val remainingExceptions: List<TrafficSignTagChange.Remove>,
)

/** [confirmed]: id → fingerprint of the proposals the user accepted. null: the default, that is
 *  exactly the proposals that only add tags ([ProposalKind.NEW]) */
fun TrafficSignWayTagging.confirmedChanges(confirmed: Map<String, String>? = null): ConfirmedChanges {
    val applied = ArrayList<TrafficSignTagChange>(signChanges)
    val appliedIds = HashSet<String>()
    // proposals are ordered so that the one they depend on comes first
    for (p in proposals) {
        val accepted = if (confirmed == null) p.isPreselected else confirmed[p.id] == p.fingerprint
        if (accepted && (p.dependsOn == null || p.dependsOn in appliedIds)) {
            appliedIds.add(p.id)
            applied.addAll(p.changes)
        }
    }
    val skipped = proposals.filter { it.id !in appliedIds }
    val remaining = skipped.filter { it.kind == ProposalKind.REMOVAL && it.family == ProposalFamily.BAN && (it.dependsOn == null || it.dependsOn in appliedIds) }
        .flatMap { it.changes.filterIsInstance<TrafficSignTagChange.Remove>() }
    return ConfirmedChanges(applied, skipped, remaining)
}

/** The changes written for the given confirmation */
fun TrafficSignWayTagging.applyConfirmed(tags: Tags, confirmed: Map<String, String>? = null) {
    for (change in confirmedChanges(confirmed).applied) {
        when (change) {
            is TrafficSignTagChange.Set -> tags[change.key] = change.value
            is TrafficSignTagChange.Remove -> tags.remove(change.key)
        }
    }
}
