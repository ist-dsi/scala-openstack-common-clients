package pt.tecnico.dsi.openstack.common.models

import io.circe.derivation.Configuration

given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
