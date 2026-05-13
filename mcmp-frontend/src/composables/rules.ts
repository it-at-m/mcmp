import type { AwxConfig } from "@/types/AwxConfig";
import type { BaasConfig } from "@/types/BaasConfig";
import type { InfobloxConfig } from "@/types/InfobloxConfig";
import type { SnowConfig } from "@/types/SnowConfig";

import installServerDetails, {
  categoryType,
} from "@/types/installServerDetails";

export function useRules() {
  function minLengthRule(length: number, message = "error") {
    return (value: string | null | undefined) =>
      !value ||
      ((value || value?.trim() == "") && value.length >= length) ||
      message;
  }

  function maxLengthRule(length: number, message = "error") {
    return (value: string | null | undefined) =>
      !value ||
      ((value || value?.trim() == "") && value.length <= length) ||
      message;
  }

  function notEmptyRule(message = "error") {
    return (value: string | null | undefined) => {
      if (value == null || value == undefined) return message;
      if (typeof value == "string") {
        return (value && value.trim() != "") || message;
      }
      return true;
    };
  }

  function regexRule(regex: RegExp, message = "error") {
    return (value: string | null | undefined) =>
      (value && regex.test(value)) || message;
  }

  function rangeRule(min: number, max: number, message = "error") {
    return (value: number | null | undefined) =>
      (value != null && value >= min && value <= max) || message;
  }

  type Config = SnowConfig | AwxConfig | InfobloxConfig | BaasConfig;
  function allowConfigSave(object: Config): boolean {
    return Object.entries(object).every(([k, v]) => {
      if (k != "proxy" && typeof v == "string") {
        return v != "";
      }
      return true;
    });
  }

  function allowNext(
    step: number,
    instlServerDetails: installServerDetails
  ): boolean {
    const validationRules = useRules();
    if (
      !instlServerDetails.category?.label.match(
        /MariaDB|PostgreSQL|MySQL|OracleDB|MSSQL/
      ) &&
      step != 1
    ) {
      step += 1;
    }
    if (!instlServerDetails.schedule && step > 4) {
      step += 1;
    }
    switch (step) {
      case 1:
        if (
          (instlServerDetails.categoryType == categoryType.DB ||
            instlServerDetails.categoryType == categoryType.Mixed) &&
          !instlServerDetails.category?.label.match(/PostgreSQL/)
        ) {
          const baseValid =
            instlServerDetails.appservice != null &&
            instlServerDetails.osType != null &&
            instlServerDetails.osVersion != null &&
            instlServerDetails.categoryType != null &&
            instlServerDetails.category != null;

          const reasonValid = nonPostgresReasonRules.every(
            (rule) => rule(instlServerDetails.nonPostgresReason) === true
          );

          return baseValid && reasonValid;
        }

        if (instlServerDetails.categoryType == "Standard") {
          return (
            instlServerDetails.appservice != null &&
            instlServerDetails.osType != null &&
            instlServerDetails.osVersion != null
          );
        }
        return (
          instlServerDetails.appservice != null &&
          instlServerDetails.osType != null &&
          instlServerDetails.osVersion != null &&
          instlServerDetails.categoryType != null &&
          instlServerDetails.category != null
        );
      case 2:
        if (
          instlServerDetails.category?.label.match(
            /MariaDB|PostgreSQL|MySQL|OracleDB/
          )
        ) {
          return validateObject(
            instlServerDetails.dbParams!.mariaPostgresMysqlOracle,
            {
              db_version: getDynamicRules(
                "db_version",
                instlServerDetails.category.label
              ),
              customer_db_name: getDynamicRules(
                "customer_db_name",
                instlServerDetails.category.label
              ),
              customer_db_user: getDynamicRules(
                "customer_db_user",
                instlServerDetails.category.label
              ),
              customer_db_schema: getDynamicRules(
                "customer_db_schema",
                instlServerDetails.category.label
              ),
              customer_db_charset: getDynamicRules(
                "customer_db_charset",
                instlServerDetails.category.label
              ),
              oracle_datasize: getDynamicRules(
                "oracle_datasize",
                instlServerDetails.category.label
              ),
            }
          );
        }
        return !!instlServerDetails.category?.label.match(/MSSQL/);

      case 3:
        return (
          instlServerDetails.expectedServerName != null &&
          instlServerDetails.expectedServerName != ""
        );
      case 4:
        return (
          instlServerDetails.memory > 0 &&
          instlServerDetails.cpu > 0 &&
          instlServerDetails.networkGroup != null &&
          instlServerDetails.appservice != null
        );
      case 5: {
        const nowPlusOne = new Date();
        nowPlusOne.setHours(nowPlusOne.getHours() + 1);
        return (
          validationRules.notEmptyRule("Abbauzeitpunkt darf nicht leer sein.")(
            instlServerDetails.removeScheduleTime.toISOString()
          ) === true &&
          validationRules.isNotPastTime(
            nowPlusOne,
            instlServerDetails.removeScheduleTime,
            "Abbauzeitpunkt muss 1h nach Aufbau Zeitpunkt liegen."
          ) === true &&
          validationRules.isNotAfterTime(
            new Date(),
            instlServerDetails.removeScheduleTime,
            14,
            "Abbauzeitpunkt darf nicht mehr als 2 Wochen in der Zukunft liegen."
          ) === true
        );
      }
      case 6:
        return true;
      default:
        return false;
    }
  }

  function notEmptySelectRule(message = "error") {
    return (value: string | null | undefined) =>
      (value && value != "") || message;
  }

  function validateObject(object: any, rules: Record<string, any[]>): boolean {
    return Object.entries(rules).every(([key, fieldRules]) => {
      const value = object[key];
      return fieldRules.every((rule) => rule(value) === true);
    });
  }

  const dbFieldRules = {
    db_version: {
      all: [notEmptySelectRule("Datenbank Version ist erforderlich")],
    },
    customer_db_schema: {
      postgresql: [
        notEmptyRule("Datenbank Schema ist erforderlich"),
        regexRule(
          /^[a-zA-Z0-9_-]+$/,
          "Nur Buchstaben, Zahlen, Bindestriche und Unterstriche erlaubt"
        ),
        maxLengthRule(20, "Maximal 20 Zeichen"),
      ],
      mariadb: [],
      mysql: [],
      oracledb: [],
    },
    customer_db_name: {
      all: [
        notEmptyRule("Datenbank Name ist erforderlich"),
        regexRule(
          /^[a-zA-Z0-9_-]+$/,
          "Nur Buchstaben, Zahlen, Bindestriche und Unterstriche erlaubt"
        ),
        maxLengthRule(20, "Maximal 20 Zeichen"),
      ],
    },
    customer_db_user: {
      postgresql: [
        notEmptyRule("Datenbank User ist erforderlich"),
        regexRule(
          /^[a-zA-Z0-9_-]+$/,
          "Nur Buchstaben, Zahlen, Bindestriche und Unterstriche erlaubt"
        ),
        maxLengthRule(20, "Maximal 20 Zeichen"),
      ],
      mariadb: [
        notEmptyRule("Datenbank User ist erforderlich"),
        regexRule(
          /^[a-zA-Z0-9_-]+$/,
          "Nur Buchstaben, Zahlen, Bindestriche und Unterstriche erlaubt"
        ),
        maxLengthRule(20, "Maximal 20 Zeichen"),
      ],
      mysql: [
        notEmptyRule("Datenbank User ist erforderlich"),
        regexRule(
          /^[a-zA-Z0-9_-]+$/,
          "Nur Buchstaben, Zahlen, Bindestriche und Unterstriche erlaubt"
        ),
        maxLengthRule(20, "Maximal 20 Zeichen"),
      ],
      oracledb: [],
    },
    customer_db_charset: {
      mysql: [notEmptySelectRule("Zeichensatz ist erforderlich")],
      mariadb: [notEmptySelectRule("Zeichensatz ist erforderlich")],
      postgresql: [],
      oracledb: [notEmptySelectRule("Zeichensatz ist erforderlich")],
    },
    oracle_datasize: {
      oracledb: [notEmptyRule("Datenbank Größe ist erforderlich")],
      mysql: [],
      mariadb: [],
      postgresql: [],
    },
  };

  function getDynamicRules(field: string, category: string) {
    const fieldRule = dbFieldRules[field as keyof typeof dbFieldRules];
    if (!fieldRule) return [];

    if ("all" in fieldRule) {
      return fieldRule.all;
    }

    return fieldRule[category.toLowerCase() as keyof typeof fieldRule] || [];
  }

  function stripLcmPrefix(value: string | null | undefined) {
    if (!value) return value;
    return value.startsWith(LCM_PREFIX)
      ? value.slice(LCM_PREFIX.length)
      : value;
  }

  // Bei Textanpassung von LCM_PREFIX muss die Variable auch in GeneralSettings.vue angepasst werden
  const LCM_PREFIX =
    "Diese Installation findet im Rahmen des Lifecyclemanagements statt. ";

  const nonPostgresReasonRules = [
    (v: string | null | undefined) => {
      const text = stripLcmPrefix(v);
      return notEmptyRule("Begründung ist erforderlich")(text);
    },

    (v: string | null | undefined) => {
      const text = stripLcmPrefix(v);
      return minLengthRule(50, "Mindestens 50 Zeichen erforderlich")(text);
    },

    (v: string | null | undefined) => {
      const text = stripLcmPrefix(v);
      return maxLengthRule(500, "Maximal 500 Zeichen")(text);
    },
  ];

  function getNonPostgresReasonRules() {
    return nonPostgresReasonRules;
  }

  function isNotPastTime(
    toCompareDate: Date,
    toValidateDate: Date,
    message = "Error"
  ) {
    return toValidateDate > toCompareDate || message;
  }

  function isNotAfterTime(
    toCompareDate: Date,
    toValidateDate: Date,
    timeSpanDays: number,
    message = "Error"
  ) {
    toCompareDate.setDate(toCompareDate.getDate() + timeSpanDays);
    return toValidateDate < toCompareDate || message;
  }

  return {
    minLengthRule,
    maxLengthRule,
    notEmptyRule,
    regexRule,
    allowNext,
    allowConfigSave,
    notEmptySelectRule,
    getDynamicRules,
    getNonPostgresReasonRules,
    isNotPastTime,
    isNotAfterTime,
    rangeRule,
  };
}
