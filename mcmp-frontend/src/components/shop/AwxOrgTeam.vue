<template>
  <common-dialog
    v-model="dialog"
    :loading="loading"
    title="AWX Organisation/Team verwalten"
    max-width="700"
    show-actions
    :submit-activated="canSubmit && !loading"
    :icon="mdiAccountGroup"
    :check-for-enabled-actions="['AWX_ENSURE_ORG']"
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        flat
        @click="open"
        >AWX Org/Team verwalten
      </v-btn>
    </template>

    <h4>Was soll verwaltet werden?</h4>
    <v-radio-group
      v-model="OrgOrTeam"
      inline
      class="mt-3"
    >
      <v-row>
        <v-radio
          :value="'org'"
          label="Organisation"
        />
        <v-radio
          :value="'team'"
          label="Team"
        />
      </v-row>
    </v-radio-group>
    <h4>Aktion</h4>
    <v-radio-group
      v-model="action"
      class="mt-3"
    >
      <v-row>
        <v-radio
          :value="'create'"
          label="Hinzufügen"
        />
        <v-radio
          :value="'edit'"
          label="Ändern"
        />
        <v-radio
          :value="'rename'"
          label="Umbenennen"
        />
      </v-row>
    </v-radio-group>
    <br />

    <v-form ref="form">
      <v-text-field
        v-if="action == 'create'"
        v-model="name"
        :label="OrgOrTeamLabel + ' Name'"
        variant="outlined"
        clearable
        maxlength="64"
        :rules="nameRules"
      />

      <v-autocomplete
        v-if="action == 'edit' || action == 'rename'"
        v-model="selectedEntity"
        v-model:search="searchText"
        :items="entityList"
        :loading="loading"
        :label="OrgOrTeamLabel + ' auswählen'"
        variant="outlined"
        clearable
        :rules="[
          rules.notEmptySelectRule(
            'Es muss ' +
              (OrgOrTeam == 'org' ? 'eine Organisation' : 'ein Team') +
              ' ausgewählt werden.'
          ),
        ]"
        @click="getEntities"
      >
        <template #no-data>
          <a class="ml-2">Keine {{ OrgOrTeamLabel }}s gefunden</a>
        </template>
      </v-autocomplete>

      <v-text-field
        v-if="action == 'rename'"
        v-model="newName"
        label="Neuer Name"
        variant="outlined"
        clearable
        maxlength="64"
        :rules="nameRules"
      />

      <template v-if="action != 'rename'">
        <v-autocomplete
          v-if="OrgOrTeam == 'team' && action == 'create'"
          v-model="teamOrganization"
          :items="orgList"
          label="Organisation des Teams"
          variant="outlined"
          clearable
          :rules="[
            rules.notEmptySelectRule(
              'Es muss eine Organisation gewählt werden.'
            ),
          ]"
        />
        <v-text-field
          v-else-if="OrgOrTeam == 'team'"
          :model-value="teamOrganization"
          label="Organisation des Teams"
          variant="outlined"
          readonly
          disabled
        />

        <v-autocomplete
          v-model="staticMembers"
          v-model:search="staticSearch"
          :items="staticItems"
          :loading="loadingStaticUsers"
          :label="staticLabel"
          hint="Benutzer suchen (mindestens 3 Zeichen)"
          persistent-hint
          variant="outlined"
          multiple
          chips
          closable-chips
          clearable
          no-filter
          class="mb-2"
          :disabled="memberFieldsDisabled"
          :rules="[memberRule(MEMBER_REGEX, 'Ungültiger Benutzername.')]"
        >
          <template #chip="{ props: chipProps, item }">
            <v-chip
              v-bind="chipProps"
              :text="String(item.value)"
            />
          </template>
          <template #no-data>
            <div class="px-4 py-2">
              {{
                staticSearch.trim().length < MIN_USER_SEARCH_LENGTH
                  ? "Mindestens 3 Zeichen eingeben"
                  : "Kein Benutzer gefunden"
              }}
            </div>
          </template>
        </v-autocomplete>
        <v-combobox
          v-model:search="ldapSearch"
          :model-value="ldapMembers"
          :items="ldapItems"
          no-filter
          :label="ldapLabel"
          hint="Abteilung (cn) suchen (mindestens 4 Zeichen)"
          persistent-hint
          variant="outlined"
          multiple
          chips
          closable-chips
          clearable
          :disabled="memberFieldsDisabled"
          :rules="[memberRule(LDAP_ENTRY_REGEX, 'Ungültige LDAP-Gruppe.')]"
          @update:model-value="onLdapMembersChange"
        >
          <template #chip="{ props: chipProps, item }">
            <v-chip
              v-bind="chipProps"
              :text="String(item.value)"
            />
          </template>
          <template #item="{ props: itemProps, item }">
            <v-list-item
              v-bind="itemProps"
              :subtitle="String(item.value)"
            />
          </template>
        </v-combobox>
        <common-alert
          color="info"
          class="mt-3 mb-6"
        >
          Bei LDAP-Gruppen werden nur direkte Gruppenmitglieder aufgelöst.
          Mitglieder von verschachtelten Untergruppen werden nicht
          berücksichtigt.
        </common-alert>
        <v-combobox
          v-if="otherMembers.length > 0"
          :model-value="otherMembers"
          :label="otherLabel"
          hint="Diese Einträge können hier nicht geändert werden, da sie aus LDAP stammen."
          persistent-hint
          variant="outlined"
          multiple
          chips
          readonly
          disabled
          class="mt-2"
        />
      </template>
    </v-form>
  </common-dialog>
</template>
<script setup lang="ts">
import { mdiAccountGroup } from "@mdi/js";
import { computed, inject, ref, watch } from "vue";

import awxOrgTeamService from "@/api/awxOrgTeamService.ts";
import jobService from "@/api/jobService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";
import { STATUS_INDICATORS } from "@/constants";
import { useSnackbarStore } from "@/stores/snackbar";

type OrgOrTeamType = "org" | "team";
type Action = "create" | "edit" | "rename";

const NAME_REGEX = /^[A-Za-z0-9._-]{1,64}$/;
const MEMBER_REGEX = /^[A-Za-z0-9._-]{1,64}$/;
const STATIC_PREFIX = "STATIC:";
const LDAP_PREFIX = "LDAP:cn=";
const LDAP_SUFFIX = ",ou=groups,ou=itm,o=lhm,c=de";

function toStaticEntry(user: string) {
  return STATIC_PREFIX + user.trim();
}

function toLdapEntry(cn: string) {
  return LDAP_PREFIX + cn.trim() + LDAP_SUFFIX;
}

const LDAP_ENTRY_REGEX =
  /^LDAP:cn=[A-Za-z0-9._-]{1,64}(,(ou|o|c)=[A-Za-z0-9._-]{1,64}){1,6}$/;

function onLdapMembersChange(values: (string | { value: string })[]) {
  const entries = values.map((raw) => {
    const value = (typeof raw == "string" ? raw : raw.value).trim();
    return value.startsWith("LDAP:") ? value : toLdapEntry(value);
  });
  ldapMembers.value = [...new Set(entries)];
}

const dialog = ref(false);
const loading = ref(false);
const rules = useRules();
const form = ref<HTMLFormElement>();
const snackbarStore = useSnackbarStore();

const OrgOrTeam = ref<OrgOrTeamType>("org");
const action = ref<Action>("create");

const name = ref("");
const newName = ref("");
const selectedEntity = ref<string | null>(null);
const searchText = ref("");

const MIN_USER_SEARCH_LENGTH = 3;
const staticSearch = ref("");
const staticItems = ref<{ title: string; value: string }[]>([]);
const loadingStaticUsers = ref(false);
let staticSearchTimeout: ReturnType<typeof setTimeout> | null = null;
const MIN_DEPARTMENT_SEARCH_LENGTH = 4;
const ldapSearch = ref("");
const ldapSuggestions = ref<string[]>([]);
const ldapItems = computed(() =>
  ldapSuggestions.value.map((cn) => ({
    title: cn,
    value: toLdapEntry(cn),
  }))
);
let ldapSearchTimeout: ReturnType<typeof setTimeout> | null = null;
const teamOrganization = ref<string | null>(null);

//editable
const staticMembers = ref<string[]>([]);
const ldapMembers = ref<string[]>([]);

//non-editable (members from resolved ldap groups)
const otherMembers = ref<string[]>([]);

const orgList = ref<string[]>([]);
const teamList = ref<string[]>([]);
const orgListRequested = ref(false);
const teamListRequested = ref(false);

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const nameRules = [
  rules.notEmptyRule("Es muss ein Name angegeben werden."),
  rules.regexRule(
    NAME_REGEX,
    "Nur Buchstaben, Ziffern sowie . _ - sind erlaubt (max. 64 Zeichen)."
  ),
];

const OrgOrTeamLabel = computed(() =>
  OrgOrTeam.value == "org" ? "Organisation" : "Team"
);

const staticLabel = computed(() =>
  OrgOrTeam.value == "org" ? "Statische Admins" : "Statische Benutzer"
);

const ldapLabel = computed(() =>
  OrgOrTeam.value == "org" ? "LDAP-Admin-Gruppen" : "LDAP-Gruppen"
);

const otherLabel = computed(() =>
  OrgOrTeam.value == "org" ? "Admins aus LDAP" : "Benutzer aus LDAP"
);

const entityList = computed(() =>
  OrgOrTeam.value == "org" ? orgList.value : teamList.value
);

const loadingDetails = ref(false);
const memberFieldsDisabled = computed(
  () =>
    action.value == "edit" && (!selectedEntity.value || loadingDetails.value)
);

// members as loaded from AWX, to detect changes while editing
const originalStatic = ref<string[]>([]);
const originalLdap = ref<string[]>([]);

function sameMembers(a: string[], b: string[]) {
  return a.length == b.length && a.every((member) => b.includes(member));
}

const hasMemberChange = computed(
  () =>
    !sameMembers(staticMembers.value, originalStatic.value) ||
    !sameMembers(ldapMembers.value, originalLdap.value)
);

const hasMembers = computed(
  () => staticMembers.value.length > 0 || ldapMembers.value.length > 0
);

const canSubmit = computed(() => {
  if (loadingDetails.value) return false;
  switch (action.value) {
    case "create":
      return (
        name.value.trim() != "" &&
        hasMembers.value &&
        (OrgOrTeam.value == "org" || !!teamOrganization.value)
      );
    case "rename":
      return !!selectedEntity.value && newName.value.trim() != "";
    case "edit":
      return !!selectedEntity.value && hasMemberChange.value;
    default:
      return false;
  }
});

function memberRule(regex: RegExp, message: string) {
  return (values: string[] | null | undefined) =>
    (values ?? []).every((value) => regex.test(value)) || message;
}

function clearMembers() {
  teamOrganization.value = null;
  staticMembers.value = [];
  ldapMembers.value = [];
  otherMembers.value = [];
  originalStatic.value = [];
  originalLdap.value = [];
}

watch([OrgOrTeam, action], () => {
  selectedEntity.value = null;
  searchText.value = "";
  clearMembers();
  if (OrgOrTeam.value == "team" && action.value == "create")
    loadOrganizations();
});

watch(selectedEntity, async (entity) => {
  searchText.value = "";
  clearMembers();
  if (!entity || action.value != "edit") return;

  const isOrg = OrgOrTeam.value == "org";
  try {
    loadingDetails.value = true;
    const details = isOrg
      ? await awxOrgTeamService.getOrganizationDetails(loading, entity)
      : await awxOrgTeamService.getTeamDetails(loading, entity);
    // ignore stale responses if the selection changed meanwhile
    if (selectedEntity.value != entity) return;
    teamOrganization.value = details.organization;
    staticMembers.value = details.staticMembers.map((entry) =>
      entry.slice(STATIC_PREFIX.length)
    );
    ldapMembers.value = details.ldapMembers;
    otherMembers.value = details.otherMembers;
    originalStatic.value = [...staticMembers.value];
    originalLdap.value = [...ldapMembers.value];
  } catch {
    selectedEntity.value = null;
  } finally {
    loadingDetails.value = false;
  }
});

function reset() {
  OrgOrTeam.value = "org";
  action.value = "create";
  name.value = "";
  newName.value = "";
  selectedEntity.value = null;
  searchText.value = "";
  clearMembers();
  staticSearch.value = "";
  staticItems.value = [];
  // lists may be stale after a job was started
  orgListRequested.value = false;
  teamListRequested.value = false;
}

function open() {
  registerOpenDialog?.();
  reset();
  dialog.value = true;
  loadLdapSuggestions("");
}

function loadLdapSuggestions(query: string) {
  awxOrgTeamService
    .getLdapGroupSuggestions(query)
    .then((suggestions) => {
      if (ldapSearch.value.trim() != query) return;
      ldapSuggestions.value = suggestions;
    })
    .catch(() => {
      ldapSuggestions.value = [];
    });
}

watch(ldapSearch, (search) => {
  if (ldapSearchTimeout) clearTimeout(ldapSearchTimeout);
  const query = (search ?? "").trim();
  if (query.length < MIN_DEPARTMENT_SEARCH_LENGTH) {
    loadLdapSuggestions("");
    return;
  }
  ldapSearchTimeout = setTimeout(() => loadLdapSuggestions(query), 300);
});

watch(staticSearch, (search) => {
  if (staticSearchTimeout) clearTimeout(staticSearchTimeout);
  const query = (search ?? "").trim();
  if (query.length < MIN_USER_SEARCH_LENGTH) {
    staticItems.value = [];
    return;
  }
  staticSearchTimeout = setTimeout(() => {
    loadingStaticUsers.value = true;
    awxOrgTeamService
      .searchUsers(query)
      .then((users) => {
        if (staticSearch.value.trim() != query) return;
        staticItems.value = users.map((user) => ({
          title: `${user.username} (${user.name})`,
          value: user.username,
        }));
      })
      .catch(() => {
        staticItems.value = [];
      })
      .finally(() => {
        loadingStaticUsers.value = false;
      });
  }, 300);
});

function close() {
  unregisterOpenDialog?.();
  reset();
  dialog.value = false;
}

function loadOrganizations() {
  if (orgListRequested.value) return;
  orgListRequested.value = true;
  awxOrgTeamService.getOrganizations(loading).then((response) => {
    orgList.value = response;
  });
}

function getEntities() {
  if (OrgOrTeam.value == "org") {
    loadOrganizations();
  } else {
    if (teamListRequested.value) return;
    teamListRequested.value = true;
    awxOrgTeamService.getTeams(loading).then((response) => {
      teamList.value = response;
    });
  }
}

function showError(message: string) {
  snackbarStore.showMessage({ message, level: STATUS_INDICATORS.ERROR });
}

async function save() {
  const validation: { valid: boolean } = await form.value?.validate();
  if (!validation.valid) return;

  const isOrg = OrgOrTeam.value == "org";
  const entityName =
    action.value == "create" ? name.value.trim() : selectedEntity.value;

  const payload: Record<string, unknown> = {
    orgOrTeam: OrgOrTeam.value,
    action: action.value,
    name: entityName,
  };

  if (action.value == "rename") {
    payload.newName = newName.value.trim();
  } else {
    if (staticMembers.value.length == 0 && ldapMembers.value.length == 0) {
      showError(
        isOrg
          ? "Es muss mindestens ein Admin angegeben werden."
          : "Es muss mindestens ein Benutzer angegeben werden."
      );
      return;
    }
    payload.static = staticMembers.value.map(toStaticEntry);
    payload.ldap = ldapMembers.value;
    if (!isOrg) payload.organization = teamOrganization.value;
  }

  if (action.value == "rename") {
    const target = newName.value.trim();
    const check = isOrg
      ? await awxOrgTeamService.checkOrganization(loading, target)
      : await awxOrgTeamService.checkTeam(loading, target);

    if (check.exists) {
      showError(
        (isOrg ? "Die Organisation " : "Das Team ") +
          `"${target}" existiert bereits.`
      );
      return;
    }
  }

  if (action.value == "create") {
    const check = isOrg
      ? await awxOrgTeamService.checkOrganization(loading, name.value.trim())
      : await awxOrgTeamService.checkTeam(loading, name.value.trim());

    if (check.exists && !check.allowed) {
      showError(
        (isOrg ? "Die Organisation " : "Das Team ") +
          `"${name.value}" existiert bereits und Sie sind nicht berechtigt, ` +
          "diese zu bearbeiten."
      );
      return;
    }
  }

  await jobService.startJob(loading, "AWX_ENSURE_ORG", -1, payload);
  close();
}
</script>
