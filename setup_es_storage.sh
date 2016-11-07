#!/bin/bash


HOST="http://localhost"
CURRENT_TS=$(date +"%s")

echo "$CURRENT_TS"

#exit 1

if [ "$1" = "trips" ] || [ "$2" = "trips" ]; then
    echo "Creating new index for trips"
    curl -XPOST "$HOST:9200/trips_$CURRENT_TS" -d '{
        "settings": {
                "refresh_interval" : "50ms",
                "analysis" : {
                    "analyzer" : {
                        "ngram" : {
                            "type" : "custom",
                            "tokenizer" : "ngram",
                            "filter" : ["standard", "lowercase"]
                        }
                    },
                    "tokenizer" : {
                        "ngram" : {
                            "token_chars": ["letter", "digit", "symbol", "punctuation", "whitespace"],
                                        "min_gram": "3",
                                        "type": "nGram",
                                        "max_gram": "8"
                        }
                    }
                }

        },
        "mappings" : {
            "trip" : {
                "dynamic_templates" : [
                    {
                        "timestamp" : {
                            "path_match" : "*_timestamp",
                            "mapping" : {
                                            "format": "yyyy-mm-dd HH:mm:ss Z",
                                            "type": "date"
                            }
                        }
                    },
                    {
                        "string_text" : {
                            "match_mapping_type" : "string",
                            "match_pattern": "regex",
                            "match" : "description|text|content",
                            "mapping" : {
                                "type" : "multi_field",
                                "fields" : {
                                    "analyzed" : {
                                        "analyzer" : "standard",
                                        "index" : "analyzed",
                                        "type" : "string"
                                    },
                                    "{name}" : {
                                        "index" : "not_analyzed",
                                        "type" : "string"
                                    }
                                }
                            }
                        }
                    },
                    {
                        "string_standard" : {
                            "match_mapping_type" : "string",
                            "match_pattern": "regex",
                            "unmatch" : "description|text|content",
                            "mapping" : {
                                "type" : "multi_field",
                                "fields" : {
                                    "ngram" : {
                                        "analyzer" : "ngram",
                                        "index" : "analyzed",
                                        "type" : "string"
                                    },
                                    "analyzed" : {
                                        "analyzer" : "standard",
                                        "index" : "analyzed",
                                        "type" : "string"
                                    },
                                    "{name}" : {
                                        "index" : "not_analyzed",
                                        "type" : "string"
                                    }
                                }
                            }
                        }
                    }

                ]
            }
        },
        "aliases" : {
            "trips" : {}
        }
    }'
fi

if [ "$1" = "users" ] || [ "$2" = "users" ]; then
    echo "Creating new index for users"

    curl -XPOST "$HOST:9200/users_$CURRENT_TS" -d '{
        "settings": {
                "refresh_interval" : "50ms",
                "analysis" : {
                    "analyzer" : {
                        "ngram" : {
                            "type" : "custom",
                            "tokenizer" : "ngram",
                            "filter" : ["standard", "lowercase"]
                        }
                    },
                    "tokenizer" : {
                        "ngram" : {
                            "token_chars": ["letter", "digit", "symbol", "punctuation", "whitespace"],
                             "min_gram": "3",
                             "type": "nGram",
                             "max_gram": "8"
                        }
                    }
                }

        },
        "mappings" : {
            "user" : {
                "properties" : {
                    "password" : { "type": "string", "index" : "not_analyzed" }
                },
                "dynamic_templates" : [
                    {
                        "timestamp" : {
                            "path_match" : "(\\.*_at|at_|_timestamp|timestamp_|time_|_time|_date|date_\\.*)|time|timestamp|date|datetime",
                            "mapping" : {
                                "format": "yyyy-mm-dd HH:mm:ss Z",
                                "type": "date"
                            }
                        }
                    },
                    {
                        "string_standard" : {
                            "mapping" : {
                                "type" : "multi_field",
                                "fields" : {
                                    "ngram" : {
                                        "analyzer" : "ngram",
                                        "index" : "analyzed",
                                        "type" : "string"
                                    },
                                    "analyzed" : {
                                        "analyzer" : "standard",
                                        "index" : "analyzed",
                                        "type" : "string"
                                    },
                                    "{name}" : {
                                        "index" : "not_analyzed",
                                        "type" : "string"
                                    }
                                }
                            },
                            "match_mapping_type" : "string",
                            "path_unmatch" : "(\\.*_description|_text|_content|description_|text_|content_\\.*)|description|text|content|password"
                        }
                    },
                    {
                        "string_text" : {
                            "mapping" : {
                                "type" : "multi_field",
                                "fields" : {

                                    "analyzed" : {
                                        "analyzer" : "standard",
                                        "index" : "analyzed",
                                        "type" : "string"
                                    },
                                    "{name}" : {
                                        "index" : "not_analyzed",
                                        "type" : "string"
                                    }
                                }
                            },
                            "path_match" : "(\\.*_description|_text|_content|description_|text_|content_\\.*)|description|text|content"
                        }
                    }

                ]
            }
        },
        "aliases" : {
            "users" : {}
        }
    }'
fi

