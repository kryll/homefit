package com.kryll.homefit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kryll.homefit.R
import com.kryll.homefit.domain.DeloadReason
import com.kryll.homefit.domain.Suggestion

/** Texto de la sugerencia, o null si no hay nada que decir. */
@Composable
fun suggestionText(s: Suggestion?, nameOf: (String) -> String): String? = when (s) {
    null, Suggestion.NoData -> null
    is Suggestion.AddReps -> stringResource(R.string.prog_add_reps, s.from, s.to)
    is Suggestion.AddSeconds -> stringResource(R.string.prog_add_seconds, s.from, s.to)
    is Suggestion.HarderVariant -> stringResource(R.string.prog_harder, nameOf(s.variantId))
    is Suggestion.AddSet -> stringResource(R.string.prog_add_set, s.from, s.to)
    is Suggestion.Reduce -> stringResource(if (s.timed) R.string.prog_reduce_seconds else R.string.prog_reduce_reps, s.from, s.to)
    is Suggestion.Hold -> stringResource(
        when (s.reason) {
            Suggestion.Reason.INCOMPLETE -> R.string.prog_hold_incomplete
            Suggestion.Reason.NO_RPE -> R.string.prog_hold_no_rpe
            Suggestion.Reason.RIGHT_EFFORT -> R.string.prog_hold_right
            Suggestion.Reason.AT_CEILING -> R.string.prog_hold_ceiling
            Suggestion.Reason.NO_ACHIEVED -> R.string.prog_hold_no_achieved
            Suggestion.Reason.DELOAD -> R.string.prog_hold_deload
        },
    )
}

@Composable
fun deloadText(r: DeloadReason): String =
    stringResource(if (r == DeloadReason.LONG_BLOCK) R.string.deload_long_block else R.string.deload_high_effort)
