require_relative 'helper'
require 'lib/schema'

describe "Schema" do
  before do
    @schema = Schema.new($mysql_master.connection, "shard_1")
  end

  describe "fetch" do
    it "reads the schema" do
      schema = @schema.fetch

      schema["sharded"].should_not be_nil
      schema["sharded"].first.should == {:column_name=>"id", :ordinal_position=>1, :column_default=>nil, :is_nullable=>"NO",
                              :data_type=>"int", :character_maximum_length=>nil, :character_octet_length=>nil, :numeric_precision=>10,
                              :numeric_scale=>0, :character_set_name=>nil, :collation_name=>nil, :column_type=>"int(10)"}
    end
  end

  describe "save" do
    it "dumps the schema to a file" do
      @schema.save
    end
  end

  describe "load" do
    it "loads the dumped schema" do
      @schema.save
      new_schema = Schema.load("shard_1", @schema.binlog_info[:file], @schema.binlog_info[:pos])
      new_schema.fetch.should == @schema.fetch
    end
  end
end
