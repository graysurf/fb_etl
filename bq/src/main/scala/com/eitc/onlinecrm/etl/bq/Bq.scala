package com.eitc.onlinecrm.etl.bq

import com.eitc.onlinecrm.etl.bq.util.BigQueryUtil
import com.google.cloud.bigquery._

class Bq {

  private val bq = BigQueryOptions.getDefaultInstance.getService

  def main(args: Array[String]): Unit = {

    println("start")

    // BqUtil.loadData("fbapi", "page_post_reaction", "gs://jetcw/fbapi/page_post_reaction/201704/page_post_reaction-2017-04-19-00-30-00")

    BigQueryUtil.copyTable("about_basic", "fb_test", "fb")
    BigQueryUtil.copyTable("about_education", "fb_test", "fb")
    BigQueryUtil.copyTable("about_family", "fb_test", "fb")
    BigQueryUtil.copyTable("about_place", "fb_test", "fb")
    BigQueryUtil.copyTable("about_work", "fb_test", "fb")
    BigQueryUtil.copyTable("allfbid", "fb_test", "fb")
    BigQueryUtil.copyTable("checkin", "fb_test", "fb")
    BigQueryUtil.copyTable("fbid_apifbid", "fb_test", "fb")
    BigQueryUtil.copyTable("friend", "fb_test", "fb")
    BigQueryUtil.copyTable("page", "fb_test", "fb")
    BigQueryUtil.copyTable("pagelike", "fb_test", "fb")

    println("finish")

  }

}
